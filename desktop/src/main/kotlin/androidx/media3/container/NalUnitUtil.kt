package androidx.media3.container

/** Just the H.264 sequence-parameter-set fields TsSniffer needs (picture height, interlace flag). */
object NalUnitUtil {
    class SpsData(val height: Int, val frameMbsOnlyFlag: Boolean)

    /** [offset] points at the NAL header byte; reads up to [limit]. */
    fun parseSpsNalUnit(data: ByteArray, offset: Int, limit: Int): SpsData {
        // Strip emulation-prevention bytes (00 00 03).
        val rbsp = java.io.ByteArrayOutputStream()
        var zeros = 0
        for (i in offset + 1 until limit) {
            val b = data[i].toInt() and 0xff
            if (zeros >= 2 && b == 3) { zeros = 0; continue }
            zeros = if (b == 0) zeros + 1 else 0
            rbsp.write(b)
        }
        val r = Bits(rbsp.toByteArray())
        val profile = r.u(8); r.u(8); r.u(8)
        r.ue()                                   // seq_parameter_set_id
        if (profile in setOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
            val chroma = r.ue()
            if (chroma == 3) r.u(1)
            r.ue(); r.ue(); r.u(1)
            if (r.u(1) == 1) {                   // scaling matrices
                repeat(if (chroma != 3) 8 else 12) { i ->
                    if (r.u(1) == 1) {
                        var last = 8; var next = 8
                        repeat(if (i < 6) 16 else 64) {
                            if (next != 0) { next = (last + r.se() + 256) % 256 }
                            last = if (next == 0) last else next
                        }
                    }
                }
            }
        }
        r.ue()                                   // log2_max_frame_num_minus4
        when (r.ue()) {                          // pic_order_cnt_type
            0 -> r.ue()
            1 -> { r.u(1); r.se(); r.se(); repeat(r.ue()) { r.se() } }
        }
        r.ue(); r.u(1)                           // max_num_ref_frames, gaps
        val mbW = r.ue() + 1
        val mbH = r.ue() + 1
        val frameMbsOnly = r.u(1) == 1
        if (!frameMbsOnly) r.u(1)
        r.u(1)                                   // direct_8x8
        var cropBottom = 0; var cropTop = 0
        if (r.u(1) == 1) { r.ue(); r.ue(); cropTop = r.ue(); cropBottom = r.ue() }
        val cropUnit = if (frameMbsOnly) 2 else 4
        val height = (2 - (if (frameMbsOnly) 1 else 0)) * mbH * 16 - (cropTop + cropBottom) * cropUnit
        check(mbW > 0)
        return SpsData(height, frameMbsOnly)
    }

    private class Bits(private val d: ByteArray) {
        private var pos = 0
        fun u(n: Int): Int { var v = 0; repeat(n) { v = (v shl 1) or bit() }; return v }
        private fun bit(): Int { val b = (d[pos shr 3].toInt() shr (7 - (pos and 7))) and 1; pos++; return b }
        fun ue(): Int {
            var z = 0
            while (bit() == 0) { z++; if (z > 31) error("bad ue") }
            return (1 shl z) - 1 + u(z)
        }
        fun se(): Int { val k = ue(); return if (k and 1 == 1) (k + 1) / 2 else -(k / 2) }
    }
}
