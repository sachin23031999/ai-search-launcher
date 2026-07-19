namespace McpServer.Models;

/// <summary>A single Settings result (a Windows Settings page or toggle).</summary>
public sealed record SettingResult(
    string Id,
    string Title,
    string Description,
    string Category,
    string DeepLinkUri,
    double Score);

/// <summary>A single file result from the Windows Search index.</summary>
public sealed record FileResult(
    string Path,
    string Name,
    string Ext,
    long Size,
    DateTime? Modified,
    string? Snippet,
    string Kind,
    double Score);

/// <summary>Optional filters the LLM can pass to files.search.</summary>
public sealed class FileFilters
{
    public string[]? Types { get; set; }
    public string? ModifiedAfter { get; set; }   // ISO date, e.g. 2025-01-01
    public string? ModifiedBefore { get; set; }
    public string[]? Locations { get; set; }      // folder path prefixes
    public int? Limit { get; set; }
}
