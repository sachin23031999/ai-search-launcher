package com.ai.search.orchestrator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.serialization.JSONElement
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import com.ai.search.model.ChainStep
import com.ai.search.model.ResultAction
import com.ai.search.model.ResultDomain
import com.ai.search.model.ResultItem
import com.ai.search.model.SearchState
import com.ai.search.model.StepStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real orchestrator: a Koog agent connected to the .NET MCP server. The LLM plans the search,
 * calls the `settings_search` / `files_search` MCP tools, and we stream each step of that chain to
 * the UI. Result items come *only* from the authoritative tool payloads (real deep links / paths);
 * the LLM's own text is used solely as a short summary.
 */
class KoogOrchestrator(
    private val llmConfig: LlmConfig,
    private val mcp: McpClient,
) : SearchOrchestrator, AutoCloseable {

    override fun search(query: String): Flow<SearchState> = channelFlow {
        val steps = LinkedHashMap<String, ChainStep>()
        val results = mutableListOf<ResultItem>()
        var state = SearchState(query = query, isRunning = true)
        var intentDone = false

        fun publish() {
            state = state.copy(steps = steps.values.toList(), results = results.sortedByDescending { it.score })
            trySend(state)
        }

        fun putStep(step: ChainStep) {
            steps[step.id] = step
            publish()
        }

        putStep(ChainStep("intent", "Understanding your request", "Interpreting \"$query\""))

        try {
            val registry = mcp.toolRegistry()

            val agent = AIAgent(
                promptExecutor = llmConfig.executor(),
                llmModel = llmConfig.model,
                toolRegistry = registry,
                systemPrompt = SYSTEM_PROMPT,
                temperature = 0.0,
            ) {
                handleEvents {
                    onLLMCallStarting {
                        if (!intentDone) {
                            intentDone = true
                            putStep(ChainStep("intent", "Understanding your request", "Planning search", StepStatus.DONE))
                        }
                    }
                    onToolCallStarting { ctx ->
                        val (id, label) = stepFor(ctx.toolName)
                        putStep(ChainStep(id, label, "Searching…", StepStatus.RUNNING))
                    }
                    onToolCallCompleted { ctx ->
                        val (id, label) = stepFor(ctx.toolName)
                        val parsed = parseToolResult(ctx)
                        results.addAll(parsed)
                        putStep(ChainStep(id, label, "${parsed.size} match${if (parsed.size == 1) "" else "es"}", StepStatus.DONE))
                    }
                    onToolCallFailed { ctx ->
                        val (id, label) = stepFor(ctx.toolName)
                        putStep(ChainStep(id, label, "failed", StepStatus.ERROR))
                    }
                }
            }

            val summary = agent.run(query)

            steps["rank"] = ChainStep("rank", "Ranking and summarizing", "done", StepStatus.DONE)
            state = state.copy(isRunning = false, summary = summary.trim())
            publish()
        } catch (t: Throwable) {
            state = state.copy(
                isRunning = false,
                error = t.message ?: "Search failed (${t::class.simpleName}).",
            )
            publish()
        }

        awaitClose { }
    }

    override fun close() = mcp.close()

    // ---- tool-result parsing -------------------------------------------------------------------

    private fun parseToolResult(ctx: ToolCallCompletedContext): List<ResultItem> = runCatching {
        val text = extractPayloadText(ctx.toolResult) ?: return emptyList()
        parsePayload(text)
    }.getOrDefault(emptyList())

    /**
     * The tool result arrives as an MCP `CallToolResult`
     * (`{ "content": [ { "type": "text", "text": "<our json>" } ], "isError": ... }`).
     * Pull out our JSON string; fall back to treating the element itself as our payload.
     */
    private fun extractPayloadText(raw: JSONElement?): String? {
        val el = raw?.toKotlinxJsonElement() ?: return null
        val obj = el as? JsonObject ?: return el.toString()

        (obj["content"] as? JsonArray)?.let { content ->
            val text = buildString {
                for (item in content) {
                    (item as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull?.let { append(it) }
                }
            }
            if (text.isNotBlank()) return text
        }
        if (obj.containsKey("results")) return obj.toString()
        return null
    }

    private fun parsePayload(text: String): List<ResultItem> {
        val root = runCatching { LENIENT.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return emptyList()
        val domain = root.str("domain", "Domain") ?: return emptyList()
        val arr = root["results"] as? JsonArray ?: root["Results"] as? JsonArray ?: return emptyList()
        return when (domain.lowercase()) {
            "settings" -> arr.mapNotNull { (it as? JsonObject)?.let(::toSettingItem) }
            "files" -> arr.mapNotNull { (it as? JsonObject)?.let(::toFileItem) }
            else -> emptyList()
        }
    }

    private fun toSettingItem(o: JsonObject): ResultItem {
        val title = o.str("Title", "title") ?: "Setting"
        val subtitle = listOfNotNull(
            o.str("Category", "category"),
            o.str("Description", "description"),
        ).joinToString(" · ")
        val deepLink = o.str("DeepLinkUri", "deepLinkUri") ?: "ms-settings:"
        return ResultItem(
            id = "settings:" + (o.str("Id", "id") ?: title),
            domain = ResultDomain.SETTINGS,
            title = title,
            subtitle = subtitle,
            action = ResultAction.OpenSettings(deepLink),
            score = o.dbl("Score", "score"),
        )
    }

    private fun toFileItem(o: JsonObject): ResultItem? {
        val path = o.str("Path", "path") ?: return null
        val name = o.str("Name", "name") ?: path.substringAfterLast('\\')
        val dir = path.substringBeforeLast('\\', "")
        val modified = o.str("Modified", "modified")?.substringBefore('T')
        val subtitle = listOfNotNull(dir.ifBlank { null }, modified).joinToString(" · ")
        return ResultItem(
            id = "file:$path",
            domain = ResultDomain.FILES,
            title = name,
            subtitle = subtitle,
            action = ResultAction.OpenFile(path),
            score = o.dbl("Score", "score"),
        )
    }

    private fun JsonObject.str(vararg keys: String): String? {
        for (k in keys) this[k]?.jsonPrimitive?.contentOrNull?.let { return it }
        return null
    }

    private fun JsonObject.dbl(vararg keys: String): Double {
        for (k in keys) this[k]?.jsonPrimitive?.doubleOrNull?.let { return it }
        return 0.0
    }

    private fun stepFor(toolName: String): Pair<String, String> = when {
        toolName.contains("settings", ignoreCase = true) -> "settings" to "Searching Settings"
        toolName.contains("files", ignoreCase = true) -> "files" to "Searching files"
        else -> toolName to "Running $toolName"
    }

    companion object {
        private val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }

        private val SYSTEM_PROMPT = """
            You are a Windows search assistant. The user types a natural-language request.
            Decide whether it concerns Windows Settings, files on this PC, or both, and CALL the
            matching tools:
              - settings_search(query): Windows Settings pages and toggles (bluetooth, dark mode, display, etc.).
              - files_search(query, ...): files on this PC via the Windows Search index.
            Call BOTH tools when the request could plausibly match either domain. Derive concise
            queries from the user's intent and prefer calling a tool over guessing. After the tools
            return, reply with ONE short sentence summarizing what was found. Never invent settings
            deep links or file paths — the UI renders the real tool results; your text is only a brief summary.
        """.trimIndent()
    }
}
