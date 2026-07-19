using System.ComponentModel;
using System.Runtime.Versioning;
using System.Text.Json;
using McpServer.Models;
using McpServer.Providers;
using ModelContextProtocol.Server;

namespace McpServer.Tools;

[McpServerToolType]
public sealed class FilesSearchTool
{
    private static readonly JsonSerializerOptions Json = new() { WriteIndented = false };

    [McpServerTool(Name = "files_search")]
    [Description("Search files on this PC using the Windows Search index. Accepts a " +
                 "natural-language query plus optional filters (file types, modified date range, " +
                 "folder locations). Returns ranked files with path, name, size and modified date.")]
    [SupportedOSPlatform("windows")]
    public string Search(
        FilesProvider provider,
        [Description("The user's natural-language file query, e.g. 'budget spreadsheet'.")] string query,
        [Description("Optional comma-separated file extensions to restrict to, e.g. 'pdf,docx'. Empty for any type.")] string types = "",
        [Description("Optional ISO date (yyyy-MM-dd); only files modified on/after this date. Empty to ignore.")] string modifiedAfter = "",
        [Description("Optional ISO date (yyyy-MM-dd); only files modified on/before this date. Empty to ignore.")] string modifiedBefore = "",
        [Description("Optional comma-separated folder path prefixes to restrict the search scope. Empty for all.")] string locations = "",
        [Description("Maximum number of results (default 15, max 50).")] int limit = 15)
    {
        var filters = new FileFilters
        {
            Types = SplitCsv(types),
            ModifiedAfter = NullIfEmpty(modifiedAfter),
            ModifiedBefore = NullIfEmpty(modifiedBefore),
            Locations = SplitCsv(locations),
            Limit = limit
        };

        try
        {
            var results = provider.Search(query, filters);
            return JsonSerializer.Serialize(new { domain = "files", count = results.Count, results }, Json);
        }
        catch (Exception ex)
        {
            return JsonSerializer.Serialize(new
            {
                domain = "files",
                count = 0,
                error = "Windows Search query failed: " + ex.Message,
                results = Array.Empty<FileResult>()
            }, Json);
        }
    }

    private static string? NullIfEmpty(string? s) => string.IsNullOrWhiteSpace(s) ? null : s.Trim();

    private static string[]? SplitCsv(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return null;
        var parts = s.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        return parts.Length == 0 ? null : parts;
    }
}
