package com.fitztv.pairing

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Tiny LAN-only web server that runs while the QR dialog is open, so a phone on the same
 * network can type in Xtream / M3U details. A random token in the URL stops anyone else on
 * the network from using it. Nothing goes via the internet.
 */
class PairingServer(private val onSubmit: (Map<String, String>) -> String?) {
    val token: String = SecureRandom().let { r -> (1..10).map { "abcdefghjkmnpqrstuvwxyz23456789"[r.nextInt(31)] }.joinToString("") }
    private var server: ServerSocket? = null
    @Volatile private var running = false

    /** Starts listening and returns the URL to encode in the QR code, or null if offline. */
    fun start(): String? {
        val ip = localIp() ?: return null
        val ss = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(8686)) } }
            .getOrElse { ServerSocket(0) }
        server = ss
        running = true
        thread(name = "pairing", isDaemon = true) {
            while (running) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
        return "http://$ip:${ss.localPort}/?t=$token"
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
    }

    private fun handle(sock: Socket) {
        sock.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        var length = 0
        while (true) {
            val h = reader.readLine() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val query = parse(target.substringAfter('?', ""))
        val out = sock.getOutputStream()
        if (query["t"] != token) return respond(out, 403, page("Link expired", "<p>Open the QR code on the TV again and rescan it.</p>"))

        if (method == "POST") {
            val buf = CharArray(length.coerceIn(0, 16_384))
            var read = 0
            while (read < buf.size) { val n = reader.read(buf, read, buf.size - read); if (n < 0) break; read += n }
            val form = parse(String(buf, 0, read))
            val error = onSubmit(form)
            return if (error == null) respond(out, 200, page("Sent to the TV ✓", "<p>Done. The TV has saved what you sent (sources load in the background; a token triggers an update check).</p><p><a href=\"/?t=$token\">Add another</a></p>"))
            else respond(out, 200, page("Couldn't add that", "<p class=err>${esc(error)}</p>") + form(token))
        }
        respond(out, 200, page("Fitz TV setup", "") + form(token))
    }

    private fun respond(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray()); out.write(bytes); out.flush()
    }

    private fun parse(s: String): Map<String, String> = s.split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(title: String, body: String) = """<!doctype html><html><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>Fitz TV</title><style>
body{margin:0;background:#07030f;color:#f5eeff;font:16px system-ui,sans-serif;padding:20px}
h1{font-size:24px;margin:0 0 4px}h1 b{color:#b026ff}.sub{color:#b9a6d9;margin:0 0 18px}
label{display:block;margin:14px 0 6px;color:#b9a6d9;font-size:14px}
input{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #7b2cbf;background:#170b2b;color:#fff;font-size:16px}
input:focus{outline:2px solid #b026ff}.tabs{display:flex;gap:8px;margin-top:8px}
.tabs label{flex:1;margin:0;text-align:center;padding:10px;border:1px solid #7b2cbf;border-radius:999px;color:#fff}
.tabs input{display:none}.tabs label:has(input:checked){background:#b026ff}
button{margin-top:22px;width:100%;padding:14px;border:0;border-radius:999px;background:linear-gradient(90deg,#b026ff,#ff2bd6);color:#fff;font-size:17px;font-weight:700}
.err{color:#ff2e63}a{color:#3df2ff}</style></head><body><h1>FITZ <b>TV</b></h1><p class=sub>${esc(title)}</p>$body"""

    private fun form(t: String) = """<form method=post action="/?t=$t">
<div class=tabs><label><input type=radio name=type value=xtream checked onchange=sw()> Xtream Codes</label>
<label><input type=radio name=type value=m3u onchange=sw()> M3U playlist</label></div>
<label>Name</label><input name=name value="My IPTV">
<div id=x><label>Server URL</label><input name=url placeholder="http://host:port" autocapitalize=off autocorrect=off>
<label>Username</label><input name=username autocapitalize=off autocorrect=off>
<label>Password</label><input name=password type=password></div>
<div id=m style=display:none><label>Playlist URL</label><input name=m3u placeholder="http://…/get.php?…" autocapitalize=off autocorrect=off></div>
<label>EPG / XMLTV guide URL (optional)</label><input name=epg placeholder="http://…/xmltv.php" autocapitalize=off autocorrect=off>
<label>GitHub token for app updates (optional — paste <b>github_pat_…</b>; you can leave the source fields empty)</label>
<input name=github_token type=password autocapitalize=off autocorrect=off placeholder="github_pat_…">
<button>Send to TV</button></form>
<script>function sw(){var x=document.querySelector('input[value=xtream]').checked;
document.getElementById('x').style.display=x?'':'none';document.getElementById('m').style.display=x?'none':''}</script></body></html>"""

    companion object {
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("eth")) 0 else 1 }   // prefer wired Shield connections
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
