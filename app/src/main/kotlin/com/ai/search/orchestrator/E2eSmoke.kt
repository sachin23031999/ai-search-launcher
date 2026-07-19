package com.ai.search.orchestrator

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * End-to-end smoke check for the REAL agent path: builds the Koog orchestrator from environment
 * config (LLM_API_KEY/LLM_PROVIDER/LLM_MODEL), spawns the .NET MCP server, and runs one natural
 * language query through the full chain (LLM plans -> MCP tools execute -> results fused).
 *
 * Requires a valid LLM key in the environment, so it is only run in CI when the LLM_API_KEY secret
 * is configured. Fails (non-zero) if the agent errors or produces no chain steps.
 *
 * Run via `gradle :app:e2eSmoke` (optionally set E2E_QUERY; defaults to a settings-oriented query).
 */
fun main() = runBlocking {
    val cfg = LlmConfig.fromEnv()
        ?: error("No LLM_API_KEY in environment; e2e requires a real key.")
    val server = McpClient.resolveServerPath()
        ?: error("MCP server not found. Build mcp-server or set MCP_SERVER_PATH.")

    val query = System.getenv("E2E_QUERY")?.takeIf { it.isNotBlank() } ?: "turn on bluetooth"
    println("[E2eSmoke] provider=${cfg.provider} model=${cfg.model.id} server=$server")
    println("[E2eSmoke] query=\"$query\"")

    val orchestrator = KoogOrchestrator(cfg, McpClient(server, McpClient.resolveDotnetRoot()))
    try {
        val states = orchestrator.search(query).toList()
        val last = states.lastOrNull() ?: error("Orchestrator emitted no state.")

        println("[E2eSmoke] steps: " + last.steps.joinToString(" -> ") { "${it.label}(${it.status})" })
        println("[E2eSmoke] results: ${last.results.size}")
        last.results.take(5).forEach { println("  - [${it.domain}] ${it.title} :: ${it.subtitle}") }
        println("[E2eSmoke] summary: ${last.summary}")

        last.error?.let { error("Agent reported an error: $it") }
        check(last.steps.isNotEmpty()) { "Expected chain steps, got none." }
        check(last.steps.any { it.id == "settings" || it.id == "files" }) {
            "Expected at least one tool step (settings/files); the LLM may not have called a tool."
        }
        println("[E2eSmoke] OK — real LLM -> MCP tools -> results path working.")
    } finally {
        orchestrator.close()
    }
}
