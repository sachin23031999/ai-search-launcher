package com.ai.search.orchestrator

import kotlinx.coroutines.runBlocking

/**
 * Dev-only smoke check for the MCP wiring: spawns the .NET server through Koog's stdio transport,
 * loads the tool registry, and verifies the expected tools are present. Requires no LLM key.
 * Run via `gradle :app:mcpSmoke` (with DOTNET_ROOT set for the local .NET runtime).
 */
fun main() = runBlocking {
    val server = McpClient.resolveServerPath()
        ?: error("MCP server not found. Build mcp-server or set MCP_SERVER_PATH.")
    println("[McpSmoke] server=$server dotnetRoot=${McpClient.resolveDotnetRoot()}")

    val client = McpClient(server, McpClient.resolveDotnetRoot())
    try {
        val registry = client.toolRegistry()
        val tools = registry.tools.map { it.descriptor.name }
        println("[McpSmoke] Connected. Tools: $tools")
        check(tools.any { it.contains("settings", true) } && tools.any { it.contains("files", true) }) {
            "Expected settings_search and files_search tools, got: $tools"
        }
        println("[McpSmoke] OK — MCP transport + tool discovery working.")
    } finally {
        client.close()
    }
}
