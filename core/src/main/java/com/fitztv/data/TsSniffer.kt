package com.fitztv.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads the first ~1–2 MB of an MPEG-TS stream and works out its real resolution and
 * frame rate: resolution from the H.264 / MPEG-2 sequence header, frame rate from the video
 * timestamps (interlaced 1080i counts as 50/60 fields a second, which is what you see).
 */
object TsSniffer {
    data class Result(val height: Int, val fps: Float)

    private const val MAX_BYTES = 2_000_000
    private const val MAX_MS = 3_500L

    fun sniff(input: InputStream): Result? {
        val data = readSome(input) ?: return null
        var start = (0 until minOf(data.size - 376, 4000)).firstOrNull {
            data[it] == 0x47.toByte() && data[it + 188] == 0x47.toByte() && data[it + 376] == 0x47.toByte()
        } ?: return null

        var pmtPid = -1
        var videoPid = -1
        var streamType = 0
        val pts = ArrayList<Long>()
        val es = ByteArrayOutputStream()
        while (start + 188 <= data.size) {
            val p = start
            start += 188
            if (data[p] != 0x47.toByte()) continue
            val pusi = (data[p + 1].toInt() and 0x40) != 0
            val pid = ((data[p + 1].toInt() and 0x1f) shl 8) or (data[p + 2].toInt() and 0xff)
            val afc = (data[p + 3].toInt() shr 4) and 3
            var off = p + 4
            if (afc == 2) continue
            if (afc == 3) off += 1 + (data[p + 4].toInt() and 0xff)
            if (off >= p + 188) continue
            when {
                pid == 0 && pusi && pmtPid < 0 -> {
                    val s = off + 1 + (data[off].toInt() and 0xff)          // pointer field
                    // PAT: first program entry after the 8-byte header.
                    var e = s + 8
                    while (e + 4 <= p + 188) {
                        val program = ((data[e].toInt() and 0xff) shl 8) or (data[e + 1].toInt() and 0xff)
                        val pmt = ((data[e + 2].toInt() and 0x1f) shl 8) or (data[e + 3].toInt() and 0xff)
                        if (program != 0) { pmtPid = pmt; break }
                        e += 4
                    }
                }
                pid == pmtPid && pusi && videoPid < 0 -> {
                    val s = off + 1 + (data[off].toInt() and 0xff)
                    val sectionLen = ((data[s + 1].toInt() and 0x0f) shl 8) or (data[s + 2].toInt() and 0xff)
                    val progInfoLen = ((data[s + 10].toInt() and 0x0f) shl 8) or (data[s + 11].toInt() and 0xff)
                    var e = s + 12 + progInfoLen
                    val end = minOf(s + 3 + sectionLen - 4, p + 188)
                    while (e + 5 <= end) {
                        val type = data[e].toInt() and 0xff
                        val epid = ((data[e + 1].toInt() and 0x1f) shl 8) or (data[e + 2].toInt() and 0xff)
                        val esInfo = ((data[e + 3].toInt() and 0x0f) shl 8) or (data[e + 4].toInt() and 0xff)
                        if (type == 0x1B || type == 0x24 || type == 0x02) { videoPid = epid; streamType = type; break }
                        e += 5 + esInfo
                    }
                }
                pid == videoPid -> {
                    var payload = off
                    if (pusi && data[off] == 0.toByte() && data[off + 1] == 0.toByte() && data[off + 2] == 1.toByte()) {
                        val flags = data[off + 7].toInt() and 0xff
                        val hdrLen = data[off + 8].toInt() and 0xff
                        if (flags and 0x80 != 0 && pts.size < 120) pts += readPts(data, off + 9)
                        payload = off + 9 + hdrLen
                    }
                    if (payload < p + 188 && es.size() < 400_000) es.write(data, payload, p + 188 - payload)
                }
            }
        }
        if (videoPid < 0) return null
        val bytes = es.toByteArray()
        var height = 0
        var interlaced = false
        var codedFps = 0f
        when (streamType) {
            0x1B -> h264Sps(bytes)?.let { (h, progressive) -> height = h; interlaced = !progressive }
            0x02 -> mpeg2(bytes)?.let { (h, f) -> height = h; codedFps = f }
        }
        var fps = codedFps.takeIf { it > 0 } ?: fpsFromPts(pts)
        if (interlaced && fps in 20f..31f) fps *= 2          // 1080i50 / 1080i60: 50 / 60 fields a second
        if (height <= 0 && fps <= 0f) return null
        return Result(height, fps)
    }

    private fun readSome(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        val deadline = System.currentTimeMillis() + MAX_MS
        while (out.size() < MAX_BYTES && System.currentTimeMillis() < deadline) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray().takeIf { it.size > 188 * 50 }
    }

    private fun readPts(d: ByteArray, i: Int): Long =
        ((d[i].toLong() and 0x0E) shl 29) or ((d[i + 1].toLong() and 0xFF) shl 22) or ((d[i + 2].toLong() and 0xFE) shl 14) or
            ((d[i + 3].toLong() and 0xFF) shl 7) or ((d[i + 4].toLong() and 0xFE) shr 1)

    /** Smallest positive gap between sorted presentation times = one frame (B-frames are reordered). */
    private fun fpsFromPts(pts: List<Long>): Float {
        if (pts.size < 6) return 0f
        val sorted = pts.distinct().sorted()
        val deltas = sorted.zipWithNext { a, b -> b - a }.filter { it in 900..7_200 }
        val frame = deltas.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return 0f
        val raw = 90_000f / frame
        return listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f).minByOrNull { abs(it - raw) }!!.takeIf { abs(it - raw) < 1.5f } ?: raw
    }

    /** First H.264 SPS: (height, progressive). */
    @OptIn(UnstableApi::class)
    private fun h264Sps(b: ByteArray): Pair<Int, Boolean>? {
        var i = 0
        while (i + 4 < b.size) {
            if (b[i] == 0.toByte() && b[i + 1] == 0.toByte() && b[i + 2] == 1.toByte() && (b[i + 3].toInt() and 0x1f) == 7) {
                var end = i + 4
                while (end + 3 < b.size && !(b[end] == 0.toByte() && b[end + 1] == 0.toByte() && (b[end + 2] == 1.toByte() || b[end + 2] == 0.toByte()))) end++
                return runCatching {
                    val sps = NalUnitUtil.parseSpsNalUnit(b, i + 3, minOf(end, b.size))
                    sps.height to sps.frameMbsOnlyFlag
                }.getOrNull()
            }
            i++
        }
        return null
    }

    /** MPEG-2 sequence header: (height, fps). */
    private fun mpeg2(b: ByteArray): Pair<Int, Float>? {
        var i = 0
        while (i + 8 < b.size) {
            if (b[i] == 0.toByte() && b[i + 1] == 0.toByte() && b[i + 2] == 1.toByte() && b[i + 3] == 0xB3.toByte()) {
                val h = ((b[i + 5].toInt() and 0x0f) shl 8) or (b[i + 6].toInt() and 0xff)
                val code = b[i + 7].toInt() and 0x0f
                val fps = listOf(0f, 23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f).getOrElse(code) { 0f }
                return h to fps
            }
            i++
        }
        return null
    }

    @Suppress("unused") private fun Float.r2() = (this * 100).roundToInt() / 100f
}
