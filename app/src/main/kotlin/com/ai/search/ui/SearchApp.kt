package com.ai.search.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.ai.search.model.SearchState
import com.ai.search.orchestrator.SearchOrchestrator
import com.ai.search.platform.Launcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Root composable: search box, live chain, summary and grouped results. */
@Composable
fun SearchApp(orchestrator: SearchOrchestrator) {
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(SearchState()) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty() || state.isRunning) return
        state = SearchState(query = q, isRunning = true)
        scope.launch {
            orchestrator.search(q)
                .catch { e ->
                    state = state.copy(isRunning = false, error = e.message ?: "Search failed")
                }
                .collect { state = it }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            SearchBox(
                value = query,
                onValueChange = { query = it },
                onSubmit = ::submit,
                enabled = !state.isRunning,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            if (state.steps.isNotEmpty()) {
                ChainProgress(state.steps, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }

            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (state.summary.isNotBlank()) {
                Text(state.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }

            ResultList(
                grouped = state.grouped,
                onActivate = { Launcher.run(it.action) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            if (!state.isRunning && state.results.isEmpty() && state.error == null &&
                state.query.isNotBlank() && state.steps.isNotEmpty()
            ) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No results for \"${state.query}\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
