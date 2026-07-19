using System.Text.Json;
using McpServer.Models;

namespace McpServer.Providers;

/// <summary>
/// Settings provider: matches natural-language-ish queries against a curated catalog
/// of Windows Settings pages (ms-settings: deep links) using lexical token + synonym scoring.
/// </summary>
public sealed class SettingsProvider : ISearchProvider
{
    public string Domain => "settings";

    private readonly List<CatalogEntry> _catalog;

    public SettingsProvider()
    {
        _catalog = LoadCatalog();
    }

    public IReadOnlyList<SettingResult> Search(string query, int limit = 8)
    {
        var qTokens = Tokenize(query);
        if (qTokens.Count == 0) return Array.Empty<SettingResult>();

        var scored = new List<(CatalogEntry entry, double score)>();
        foreach (var e in _catalog)
        {
            double score = ScoreEntry(e, qTokens);
            if (score > 0) scored.Add((e, score));
        }

        return scored
            .OrderByDescending(x => x.score)
            .Take(limit)
            .Select(x => new SettingResult(
                x.entry.Id,
                x.entry.Title,
                x.entry.Description,
                x.entry.Category,
                x.entry.DeepLink,
                Math.Round(x.score, 3)))
            .ToList();
    }

    private static double ScoreEntry(CatalogEntry e, List<string> qTokens)
    {
        // Build the searchable token bag with weights.
        var titleTokens = Tokenize(e.Title);
        var synonymTokens = e.Synonyms.SelectMany(Tokenize).ToHashSet();
        var descTokens = Tokenize(e.Description).ToHashSet();
        var categoryTokens = Tokenize(e.Category).ToHashSet();

        double score = 0;
        foreach (var qt in qTokens)
        {
            if (titleTokens.Contains(qt)) score += 3.0;
            else if (synonymTokens.Contains(qt)) score += 2.5;
            else if (categoryTokens.Contains(qt)) score += 1.5;
            else if (descTokens.Contains(qt)) score += 1.0;
            else
            {
                // partial / prefix match fallback
                if (titleTokens.Any(t => t.StartsWith(qt) || qt.StartsWith(t))) score += 1.2;
                else if (synonymTokens.Any(t => t.StartsWith(qt) || qt.StartsWith(t))) score += 1.0;
            }
        }

        // Normalize a bit by query length so long queries don't dominate.
        return score / Math.Sqrt(qTokens.Count);
    }

    private static List<string> Tokenize(string s)
    {
        if (string.IsNullOrWhiteSpace(s)) return new List<string>();
        return s.ToLowerInvariant()
            .Split(new[] { ' ', '\t', '\n', '-', '_', '/', ',', '.', ':', ';', '(', ')', '&' },
                StringSplitOptions.RemoveEmptyEntries)
            .Where(t => t.Length > 1 && !StopWords.Contains(t))
            .ToList();
    }

    private static readonly HashSet<string> StopWords = new(StringComparer.OrdinalIgnoreCase)
    {
        "the", "a", "an", "to", "my", "me", "on", "off", "of", "for", "in", "is",
        "how", "do", "i", "can", "please", "turn", "open", "show", "change", "set", "go", "and"
    };

    private static List<CatalogEntry> LoadCatalog()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "Data", "settings-catalog.json");
        if (!File.Exists(path))
            return new List<CatalogEntry>();
        var json = File.ReadAllText(path);
        var entries = JsonSerializer.Deserialize<List<CatalogEntry>>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        return entries ?? new List<CatalogEntry>();
    }

    private sealed class CatalogEntry
    {
        public string Id { get; set; } = "";
        public string Title { get; set; } = "";
        public string Description { get; set; } = "";
        public string Category { get; set; } = "";
        public string DeepLink { get; set; } = "";
        public List<string> Synonyms { get; set; } = new();
    }
}
