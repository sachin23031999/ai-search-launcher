package com.ai.search.orchestrator

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File

/**
 * Spawns and supervises the local .NET MCP search server and exposes its tools as a Koog
 * [ToolRegistry]. The process and registry are created lazily on first use and reused across
 * searches; [close] tears the child process down.
 */
class McpClient(
    private val serverPath: String,
    private val dotnetRoot: String?,
) : AutoCloseable {

    private val initMutex = Mutex()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var registry: ToolRegistry? = null

    suspend fun toolRegistry(): ToolRegistry {
        registry?.let { return it }
        return initMutex.withLock {
            registry?.let { return it }
            val p = spawn()
            // Build an MCP stdio transport over the child process's stdin/stdout, then load its tools.
            val transport = StdioClientTransport(
                input = p.inputStream.asSource().buffered(),
                output = p.outputStream.asSink().buffered(),
            )
            val reg = McpToolRegistryProvider.fromTransport(
                transport = transport,
                serverInfo = McpServerInfo(command = serverPath),
            )
            process = p
            registry = reg
            reg
        }
    }

    private fun spawn(): Process {
        val pb = ProcessBuilder(serverPath)
        // The server's build output is a framework-dependent apphost; point it at our .NET runtime.
        if (!dotnetRoot.isNullOrBlank()) {
            val env = pb.environment()
            env["DOTNET_ROOT"] = dotnetRoot
            val sep = File.pathSeparator
            env["PATH"] = dotnetRoot + sep + (System.getenv("PATH") ?: "")
        }
        // The server speaks MCP on stdout/stdin and logs to stderr; surface its logs on our console.
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        return pb.start()
    }

    override fun close() {
        process?.destroy()
        process = null
        registry = null
    }

    companion object {
        /**
         * Resolves the MCP server executable path. Checks `MCP_SERVER_PATH`, then the Compose
         * packaged resources dir, then common dev build-output locations. Returns null if not found.
         */
        fun resolveServerPath(): String? {
            System.getenv("MCP_SERVER_PATH")?.let { p ->
                if (p.isNotBlank() && File(p).isFile) return File(p).absolutePath
            }

            val candidates = mutableListOf<File>()

            // Packaged app image: <resources>/mcp-server/McpServer.exe
            System.getProperty("compose.application.resources.dir")?.let { res ->
                candidates += File(res, "mcp-server/McpServer.exe")
            }

            // Dev: resolve relative to the working dir and its ancestors.
            val roots = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .take(4)
            val relPaths = listOf(
                "mcp-server/bin/Debug/net8.0-windows/McpServer.exe",
                "mcp-server/bin/Release/net8.0-windows/McpServer.exe",
                "app/resources/mcp-server/McpServer.exe",
                "resources/mcp-server/McpServer.exe",
            )
            for (root in roots) for (rel in relPaths) candidates += File(root, rel)

            return candidates.firstOrNull { it.isFile }?.absolutePath
        }

        /** Local .NET runtime root, if the server needs a bundled framework-dependent runtime. */
        fun resolveDotnetRoot(): String? = System.getenv("DOTNET_ROOT")?.takeIf { it.isNotBlank() }
    }
}
