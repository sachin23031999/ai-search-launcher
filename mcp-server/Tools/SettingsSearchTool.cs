using System.ComponentModel;
using System.Text.Json;
using McpServer.Providers;
using ModelContextProtocol.Server;

namespace McpServer.Tools;

[McpServerToolType]
public sealed class SettingsSearchTool
{
    private static readonly JsonSerializerOptions Json = new() { WriteIndented = false };

    [McpServerTool(Name = "settings_search")]
    [Description("Search Windows Settings pages and toggles by natural-language intent " +
                 "(e.g. 'turn off bluetooth', 'change display resolution', 'dark mode'). " +
                 "Returns matching settings with an ms-settings: deep link to open each one.")]
    public string Search(
        SettingsProvider provider,
        [Description("The user's natural-language settings query.")] string query,
        [Description("Maximum number of results to return (default 8).")] int limit = 8)
    {
        var results = provider.Search(query, limit);
        return JsonSerializer.Serialize(new { domain = "settings", count = results.Count, results }, Json);
    }
}
