using McpServer.Providers;
using McpServer.Tools;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

// Dev self-test: run providers directly, bypassing MCP. Usage: McpServer --selftest [query]
if (args.Length > 0 && args[0] == "--selftest")
{
    string q = args.Length > 1 ? args[1] : "budget";
    Console.Error.WriteLine($"[selftest] settings query: 'turn off bluetooth'");
    var settings = new SettingsProvider().Search("turn off bluetooth");
    foreach (var s in settings) Console.Error.WriteLine($"  SETTING {s.Title} ({s.Score}) -> {s.DeepLinkUri}");

    Console.Error.WriteLine($"[selftest] files query: '{q}'");
    try
    {
        var files = new FilesProvider().Search(q, new McpServer.Models.FileFilters { Limit = 5 });
        Console.Error.WriteLine($"  files count: {files.Count}");
        foreach (var f in files) Console.Error.WriteLine($"  FILE {f.Name} ({f.Score}) -> {f.Path}");
    }
    catch (Exception ex)
    {
        Console.Error.WriteLine($"  FILES ERROR: {ex}");
    }
    return;
}

var builder = Host.CreateApplicationBuilder(args);

// IMPORTANT: stdio transport uses stdout for the MCP protocol.
// All logging must go to stderr so it never corrupts the protocol stream.
builder.Logging.ClearProviders();
builder.Logging.AddConsole(o => o.LogToStandardErrorThreshold = LogLevel.Trace);
builder.Logging.SetMinimumLevel(LogLevel.Warning);

// Providers as singletons so tool methods can receive them via DI.
builder.Services.AddSingleton<SettingsProvider>();
builder.Services.AddSingleton<FilesProvider>();

builder.Services
    .AddMcpServer()
    .WithStdioServerTransport()
    // Register tool types explicitly (deterministic across environments) rather than
    // relying on entry-assembly reflection scanning, which can discover zero tools when
    // the host is launched via the framework-dependent apphost on CI runners.
    .WithTools<SettingsSearchTool>()
    .WithTools<FilesSearchTool>();

await builder.Build().RunAsync();
