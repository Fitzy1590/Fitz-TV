using System.Runtime.InteropServices;

namespace FitzTV.Windows.Services.Media;

/// <summary>Minimal libmpv client API (libmpv-2.dll). Names and signatures follow mpv's client.h.</summary>
internal static class MpvInterop
{
    private const string Dll = "libmpv-2.dll";
    public const int FormatDouble = 5;

    [DllImport(Dll)] public static extern nint mpv_create();
    [DllImport(Dll)] public static extern int mpv_initialize(nint ctx);
    [DllImport(Dll)] public static extern void mpv_terminate_destroy(nint ctx);
    [DllImport(Dll)] public static extern int mpv_set_option_string(nint ctx,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string name, [MarshalAs(UnmanagedType.LPUTF8Str)] string data);
    [DllImport(Dll)] public static extern int mpv_set_property_string(nint ctx,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string name, [MarshalAs(UnmanagedType.LPUTF8Str)] string data);
    [DllImport(Dll)] public static extern int mpv_command(nint ctx, nint[] args);
    [DllImport(Dll)] public static extern int mpv_get_property(nint ctx,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string name, int format, out double data);

    /// <summary>Runs an mpv command with a null-terminated array of UTF-8 strings.</summary>
    public static int Command(nint ctx, params string[] args)
    {
        var ptrs = new nint[args.Length + 1];
        try
        {
            for (var i = 0; i < args.Length; i++) ptrs[i] = Marshal.StringToCoTaskMemUTF8(args[i]);
            return mpv_command(ctx, ptrs);
        }
        finally { foreach (var p in ptrs) if (p != 0) Marshal.FreeCoTaskMem(p); }
    }
}
