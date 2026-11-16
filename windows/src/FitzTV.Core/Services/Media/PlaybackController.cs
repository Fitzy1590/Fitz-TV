namespace FitzTV.Core.Services.Media;

/// <summary>What the controller needs from a player. The Windows app implements it over libmpv.</summary>
public interface IPlayerEngine
{
    void Play(string url, string? userAgent, string? referrer);
    void Stop();
    /// <summary>Playback position in seconds, or null while not playing yet.</summary>
    double? TimePos { get; }
}

/// <summary>
/// Stall watchdog with failover across candidate streams for one channel or game.
/// Drive it by calling <see cref="Tick"/> about once a second from the UI thread.
/// </summary>
public sealed class PlaybackController(IPlayerEngine engine, TimeSpan? startTimeout = null, TimeSpan? stallTimeout = null)
{
    private readonly TimeSpan _startTimeout = startTimeout ?? TimeSpan.FromSeconds(15);
    private readonly TimeSpan _stallTimeout = stallTimeout ?? TimeSpan.FromSeconds(8);
    private IReadOnlyList<Channel> _candidates = [];
    private int _index;
    private DateTimeOffset _lastProgress;
    private double? _lastPos;
    private bool _started;

    public Channel? Current => _index < _candidates.Count ? _candidates[_index] : null;
    public bool Failed { get; private set; }
    public event Action<Channel>? Switched;

    public void Start(IReadOnlyList<Channel> candidates, DateTimeOffset now)
    {
        _candidates = candidates; _index = 0; Failed = candidates.Count == 0;
        if (!Failed) Begin(now);
    }

    public void Stop() { engine.Stop(); _candidates = []; Failed = false; }

    public void Tick(DateTimeOffset now)
    {
        if (Current == null || Failed) return;
        var pos = engine.TimePos;
        if (pos != null && (_lastPos == null || pos > _lastPos + 0.05))
        {
            _lastPos = pos; _lastProgress = now; _started = true; return;
        }
        var limit = _started ? _stallTimeout : _startTimeout;
        if (now - _lastProgress < limit) return;
        if (_index + 1 >= _candidates.Count) { Failed = true; engine.Stop(); return; }
        _index++;
        Begin(now);
    }

    private void Begin(DateTimeOffset now)
    {
        var c = _candidates[_index];
        _lastPos = null; _started = false; _lastProgress = now;
        engine.Play(c.Url, c.UserAgent, c.Referrer);
        Switched?.Invoke(c);
    }
}
