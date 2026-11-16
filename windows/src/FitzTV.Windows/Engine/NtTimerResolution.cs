using System.Runtime.InteropServices;

namespace FitzTV.Windows.Engine;

/// <summary>Locks the Windows system timer to 0.5 ms for steady frame pacing.</summary>
public static class NtTimerResolution
{
    private const uint HalfMs = 5000; // units of 100 ns

    [DllImport("ntdll.dll")]
    private static extern int NtSetTimerResolution(uint desired, [MarshalAs(UnmanagedType.U1)] bool set, out uint current);

    public static bool Active { get; private set; }

    public static void Lock() => Active = NtSetTimerResolution(HalfMs, true, out _) == 0;

    public static void Release()
    {
        if (Active) NtSetTimerResolution(HalfMs, false, out _);
        Active = false;
    }
}
