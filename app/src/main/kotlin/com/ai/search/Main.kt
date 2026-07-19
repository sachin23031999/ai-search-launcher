package com.ai.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ai.search.orchestrator.KoogOrchestrator
import com.ai.search.orchestrator.LlmConfig
import com.ai.search.orchestrator.McpClient
import com.ai.search.orchestrator.MockOrchestrator
import com.ai.search.orchestrator.SearchOrchestrator
import com.ai.search.ui.SearchApp

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(720.dp, 560.dp),
        position = WindowPosition(Alignment.Center),
    )

    // Use the real Koog + MCP orchestrator when an LLM key and the MCP server are available;
    // otherwise fall back to the dependency-free mock so the UI still runs.
    val orchestrator: SearchOrchestrator = remember {
        val cfg = LlmConfig.fromEnv()
        val serverPath = McpClient.resolveServerPath()
        if (cfg != null && serverPath != null) {
            println("[AISearch] Real orchestrator: ${cfg.provider} / ${cfg.model.id}, server=$serverPath")
            KoogOrchestrator(cfg, McpClient(serverPath, McpClient.resolveDotnetRoot()))
        } else {
            val reason = if (cfg == null) "no LLM_API_KEY" else "MCP server not found (set MCP_SERVER_PATH)"
            println("[AISearch] Mock orchestrator ($reason)")
            MockOrchestrator()
        }
    }

    DisposableEffect(orchestrator) {
        onDispose { (orchestrator as? AutoCloseable)?.close() }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "AI Search Launcher",
        state = windowState,
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            SearchApp(orchestrator)
        }
    }
}
