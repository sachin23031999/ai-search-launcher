package com.ai.search.model

/** Status of a single step in the agent's multi-step chain. */
enum class StepStatus { RUNNING, DONE, ERROR }

/**
 * A single visible step in the search chain (e.g. "Understanding intent",
 * "Searching settings", "Searching files", "Ranking results"). Streamed to the UI
 * so the user can watch the chain progress gracefully.
 */
data class ChainStep(
    val id: String,
    val label: String,
    val detail: String = "",
    val status: StepStatus = StepStatus.RUNNING,
)
