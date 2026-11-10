package com.fitztv.data

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    /** One shared client: keeps connections warm so channel switches start fast. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * For a stream that keeps dropping: IPv4 addresses first (some providers have flaky IPv6),
     * HTTP/1.1 and no kept-alive connections, so every retry opens a fresh connection.
     */
    val steady: OkHttpClient = client.newBuilder()
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
        })
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .build()

    /** Separate client for big playlist / EPG downloads. */
    val bulk: OkHttpClient = client.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun get(url: String, userAgent: String, big: Boolean = false): okhttp3.Response {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val resp = (if (big) bulk else client).newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("HTTP ${resp.code}")
        }
        return resp
    }

    fun getString(url: String, userAgent: String, big: Boolean = false): String =
        get(url, userAgent, big).use { it.body!!.string() }

    private var lastTsdb = 0L

    /** TheSportsDB's free API allows ~30 requests a minute: space calls out so none get refused. */
    @Synchronized
    fun throttleSportsDb() {
        val wait = lastTsdb + 2_100 - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastTsdb = System.currentTimeMillis()
    }
}
