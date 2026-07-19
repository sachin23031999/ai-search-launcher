package com.ai.search.orchestrator

import com.ai.search.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A dependency-free orchestrator that fakes the chain so the UI can be developed and
 * demoed without an LLM or the MCP server. Mirrors the shape of the real chain.
 */
class MockOrchestrator : SearchOrchestrator {
    override fun search(query: String): Flow<SearchState> = flow {
        val steps = mutableListOf<ChainStep>()
        var state = SearchState(query = query, isRunning = true)

        suspend fun push(step: ChainStep) {
            steps.removeAll { it.id == step.id }
            steps.add(step)
            state = state.copy(steps = steps.toList())
            emit(state)
            delay(350)
        }

        push(ChainStep("intent", "Understanding your request", "Interpreting \"$query\""))
        push(ChainStep("intent", "Understanding your request", "Detected: settings + files", StepStatus.DONE))
        push(ChainStep("settings", "Searching Settings"))
        push(ChainStep("settings", "Searching Settings", "2 matches", StepStatus.DONE))
        push(ChainStep("files", "Searching files"))
        push(ChainStep("files", "Searching files", "3 matches", StepStatus.DONE))
        push(ChainStep("rank", "Ranking and summarizing"))
        push(ChainStep("rank", "Ranking and summarizing", "done", StepStatus.DONE))

        val results = listOf(
            ResultItem("s1", ResultDomain.SETTINGS, "Bluetooth devices",
                "System · turn Bluetooth on or off", ResultAction.OpenSettings("ms-settings:bluetooth"), 0.98),
            ResultItem("s2", ResultDomain.SETTINGS, "Colors and dark mode",
                "Personalization · appearance", ResultAction.OpenSettings("ms-settings:colors"), 0.72),
            ResultItem("f1", ResultDomain.FILES, "Q3 Budget.xlsx",
                "C:\\Users\\me\\Documents · 2 days ago", ResultAction.OpenFile("C:\\Users\\me\\Documents\\Q3 Budget.xlsx"), 0.91),
        )

        state = state.copy(
            isRunning = false,
            results = results,
            summary = "Found ${results.size} results across Settings and Files for \"$query\".",
        )
        emit(state)
    }
}
