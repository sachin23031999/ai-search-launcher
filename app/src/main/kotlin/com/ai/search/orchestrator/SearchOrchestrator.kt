package com.ai.search.orchestrator

import com.ai.search.model.SearchState
import kotlinx.coroutines.flow.Flow

/**
 * Drives a natural-language query through the multi-step chain and emits progressively
 * updated [SearchState] snapshots (chain steps first, then final results).
 */
interface SearchOrchestrator {
    fun search(query: String): Flow<SearchState>
}
