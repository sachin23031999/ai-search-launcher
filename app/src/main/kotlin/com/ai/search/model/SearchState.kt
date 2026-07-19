package com.ai.search.model

/**
 * Immutable UI state for one search. The orchestrator emits updated copies as the
 * chain progresses so Compose can render the steps and final results reactively.
 */
data class SearchState(
    val query: String = "",
    val isRunning: Boolean = false,
    val steps: List<ChainStep> = emptyList(),
    val results: List<ResultItem> = emptyList(),
    val summary: String = "",
    val error: String? = null,
) {
    /** Results grouped by domain, in a stable display order. */
    val grouped: List<Pair<ResultDomain, List<ResultItem>>>
        get() = ResultDomain.entries
            .map { d -> d to results.filter { it.domain == d } }
            .filter { it.second.isNotEmpty() }
}
