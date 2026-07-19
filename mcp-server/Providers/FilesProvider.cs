using System.Data.OleDb;
using System.Runtime.Versioning;
using System.Text;
using McpServer.Models;

namespace McpServer.Providers;

/// <summary>
/// Files provider backed by the Windows Search Indexer (SystemIndex) via the
/// Search.CollatorDSO OLE DB provider. Translates a natural-language query + optional
/// filters into Advanced Query Syntax SQL and returns ranked file results.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class FilesProvider : ISearchProvider
{
    public string Domain => "files";

    private const string ConnectionString =
        "Provider=Search.CollatorDSO;Extended Properties='Application=Windows'";

    public IReadOnlyList<FileResult> Search(string query, FileFilters? filters = null)
    {
        int limit = Math.Clamp(filters?.Limit ?? 15, 1, 50);
        var tokens = Tokenize(query);
        if (tokens.Count == 0 && (filters?.Types is null || filters.Types.Length == 0))
            return Array.Empty<FileResult>();

        string sql = BuildSql(query, tokens, filters, limit);

        var results = new List<FileResult>();
        using var conn = new OleDbConnection(ConnectionString);
        conn.Open();
        using var cmd = new OleDbCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            string path = reader["System.ItemPathDisplay"] as string ?? "";
            if (string.IsNullOrEmpty(path)) continue;
            string name = reader["System.ItemNameDisplay"] as string ?? Path.GetFileName(path);
            string ext = reader["System.FileExtension"] as string ?? Path.GetExtension(path);
            long size = reader["System.Size"] is long s ? s
                        : (reader["System.Size"] is decimal d ? (long)d : 0);
            DateTime? modified = reader["System.DateModified"] as DateTime?;
            string kind = ReadKind(reader["System.Kind"]);

            results.Add(new FileResult(
                Path: path,
                Name: name,
                Ext: ext,
                Size: size,
                Modified: modified,
                Snippet: null,
                Kind: kind,
                // Rank from Windows Search is 0-1000; normalize to 0-1.
                Score: NormalizeRank(reader["System.Search.Rank"])));
        }
        return results;
    }

    private static string BuildSql(string rawQuery, List<string> tokens, FileFilters? f, int limit)
    {
        var sb = new StringBuilder();
        sb.Append("SELECT TOP ").Append(limit).Append(' ');
        sb.Append("System.ItemPathDisplay, System.ItemNameDisplay, System.FileExtension, ");
        sb.Append("System.Size, System.DateModified, System.Kind, System.Search.Rank ");
        sb.Append("FROM SystemIndex WHERE ");

        var predicates = new List<string>();

        // Full-text: match filename (prefix) OR free-text over content/properties.
        if (tokens.Count > 0)
        {
            string containsArg = string.Join(" OR ", tokens.Select(t => $"\"{t}*\""));
            string freetextArg = EscapeSql(rawQuery);
            predicates.Add(
                $"(CONTAINS(System.ItemNameDisplay, '{EscapeSql(containsArg)}') OR FREETEXT('{freetextArg}'))");
        }

        // File type / extension filter.
        if (f?.Types is { Length: > 0 })
        {
            var exts = f.Types.Select(t => t.StartsWith('.') ? t : "." + t)
                              .Select(t => $"System.FileExtension = '{EscapeSql(t.ToLowerInvariant())}'");
            predicates.Add("(" + string.Join(" OR ", exts) + ")");
        }

        // Date range.
        if (DateTime.TryParse(f?.ModifiedAfter, out var after))
            predicates.Add($"System.DateModified >= '{after:yyyy-MM-dd}'");
        if (DateTime.TryParse(f?.ModifiedBefore, out var before))
            predicates.Add($"System.DateModified <= '{before:yyyy-MM-dd}'");

        // Location scope.
        if (f?.Locations is { Length: > 0 })
        {
            var scopes = f.Locations.Select(l =>
                $"SCOPE = 'file:{EscapeSql(l.Replace('\\', '/'))}'");
            predicates.Add("(" + string.Join(" OR ", scopes) + ")");
        }

        // Exclude folders.
        predicates.Add("System.ItemType != 'Directory'");

        if (predicates.Count == 0) predicates.Add("System.ItemType != 'Directory'");

        sb.Append(string.Join(" AND ", predicates));
        sb.Append(" ORDER BY System.Search.Rank DESC");
        return sb.ToString();
    }

    private static double NormalizeRank(object? rank)
    {
        double r = rank switch
        {
            int i => i,
            long l => l,
            double d => d,
            decimal m => (double)m,
            _ => 0
        };
        return Math.Round(Math.Clamp(r / 1000.0, 0, 1), 3);
    }

    private static string ReadKind(object? kind)
    {
        if (kind is string[] arr && arr.Length > 0) return arr[0];
        if (kind is string s) return s;
        return "file";
    }

    private static List<string> Tokenize(string s)
    {
        if (string.IsNullOrWhiteSpace(s)) return new List<string>();
        return s.ToLowerInvariant()
            .Split(new[] { ' ', '\t', '\n', '-', '_', '/', ',', '.', ':', ';', '(', ')', '&', '"', '\'' },
                StringSplitOptions.RemoveEmptyEntries)
            .Where(t => t.Length > 1 && !StopWords.Contains(t))
            .Distinct()
            .Take(8)
            .ToList();
    }

    private static string EscapeSql(string s) => s.Replace("'", "''");

    private static readonly HashSet<string> StopWords = new(StringComparer.OrdinalIgnoreCase)
    {
        "the", "a", "an", "to", "my", "me", "of", "for", "in", "is", "on",
        "how", "do", "i", "can", "please", "find", "open", "show", "file",
        "files", "document", "documents", "that", "from", "with", "about", "last", "week"
    };
}
