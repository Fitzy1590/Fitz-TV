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
}
