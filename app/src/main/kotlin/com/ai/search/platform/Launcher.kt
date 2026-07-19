package com.ai.search.platform

import com.ai.search.model.ResultAction
import java.awt.Desktop
import java.io.File

/** Executes a result's primary action using supported Windows mechanisms. */
object Launcher {
    fun run(action: ResultAction) {
        when (action) {
            is ResultAction.OpenSettings -> openUri(action.deepLink)
            is ResultAction.OpenFile -> openFile(action.path)
            is ResultAction.RevealFile -> reveal(action.path)
        }
    }

    private fun openUri(uri: String) {
        // ms-settings: URIs are opened by the shell via `explorer` / `start`.
        runCatching {
            ProcessBuilder("cmd", "/c", "start", "", uri).start()
        }
    }

    private fun openFile(path: String) {
        runCatching {
            val f = File(path)
            if (Desktop.isDesktopSupported() && f.exists()) {
                Desktop.getDesktop().open(f)
            } else {
                ProcessBuilder("cmd", "/c", "start", "", path).start()
            }
        }
    }

    private fun reveal(path: String) {
        runCatching {
            ProcessBuilder("explorer.exe", "/select,", path).start()
        }
    }
}
