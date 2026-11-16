namespace FitzTV.Windows.Engine;

/// <summary>Opens connections to likely next channels so switching skips DNS/TLS setup.</summary>
public static class StreamPrewarm
{
    private static readonly HttpClient Http = new(new SocketsHttpHandler
    {
        PooledConnectionLifetime = TimeSpan.FromMinutes(5),
        EnableMultipleHttp2Connections = true,
    }) { Timeout = TimeSpan.FromSeconds(4) };

    public static async Task WarmAsync(IEnumerable<string> urls)
    {
        foreach (var u in urls.Take(4))
        {
            try { using var r = await Http.SendAsync(new HttpRequestMessage(HttpMethod.Head, u), HttpCompletionOption.ResponseHeadersRead); }
            catch { /* best effort */ }
        }
    }
}
