namespace McpServer.Providers;

/// <summary>Common contract every search domain provider implements.</summary>
public interface ISearchProvider
{
    /// <summary>Domain identifier, e.g. "settings" or "files".</summary>
    string Domain { get; }
}
