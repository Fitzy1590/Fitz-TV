package com.fitztv.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.fitztv.FitzApp
import com.fitztv.R
import com.fitztv.alerts.Alerts
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Fitz TV" row on the Shield's home screen: your teams' live / upcoming games and the biggest
 * live games. Selecting one opens it straight in the app (fitztv://game/<id>).
 */
object HomeRow {
    private const val PREFS = "homerow"

    suspend fun publish(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val app = FitzApp.instance
            val s = app.settings.value
            val now = System.currentTimeMillis()
            val games = app.repo.games.value
            val picks = (
                games.filter { it.gameState == GameState.LIVE && s.isFavourite(it) } +
                    games.filter { it.gameState == GameState.PRE && s.isFavourite(it) && it.startUtcMillis - now < 48 * 3_600_000L }.sortedBy { it.startUtcMillis } +
                    app.repo.liveNow(null).filter { it.featured }
                ).distinctBy { it.id }.take(12)

            val helper = PreviewChannelHelper(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var channelId = prefs.getLong("channel", -1L)
            if (channelId == -1L || helper.getPreviewChannel(channelId) == null) {
                channelId = helper.publishChannel(
                    PreviewChannel.Builder()
                        .setDisplayName("Fitz TV – My teams & live")
                        .setDescription("Your teams' games and what's live now")
                        .setAppLinkIntentUri(Uri.parse("fitztv://game/home"))
                        .setLogo(logo(context))
                        .build()
                )
                prefs.edit().putLong("channel", channelId).apply()
                runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
            }
            // Replace the programmes.
            prefs.getStringSet("programs", emptySet())!!.forEach { id -> runCatching { helper.deletePreviewProgram(id.toLong()) } }
            val ids = picks.mapNotNull { g -> runCatching { helper.publishPreviewProgram(program(channelId, g, s.isFavourite(g))) }.getOrNull()?.toString() }
            prefs.edit().putStringSet("programs", ids.toSet()).apply()
        }
    }

    private fun program(channelId: Long, g: Game, fav: Boolean): PreviewProgram {
        val s = FitzApp.instance.settings.value
        val title = (if (fav) "★ " else "") + g.matchup
        val desc = when (g.gameState) {
            GameState.LIVE -> if (g.home.isNotBlank()) "LIVE ${g.awayShort} ${g.awayScore ?: ""}–${g.homeScore ?: ""} ${g.homeShort} • ${g.statusText}" else "LIVE • ${g.statusText}"
            else -> "${g.league} • ${Fmt.whenLabel(g, s.zone(), s.use24h)}"
        }
        val art = g.homeLogo ?: g.awayLogo
        val b = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_EVENT)
            .setTitle(title)
            .setDescription(desc)
            .setLive(g.gameState == GameState.LIVE)
            .setIntentUri(Alerts.gameUri(g.id))
            .setInternalProviderId(g.id)
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1)
        if (art != null) b.setPosterArtUri(Uri.parse(art))
        return b.build()
    }

    private fun logo(context: Context): Bitmap {
        val d = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, 256, 256)
        d.draw(c)
        return bmp
    }
}
