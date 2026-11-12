package com.fitztv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.SportsGolf
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material.icons.outlined.SportsHockey
import androidx.compose.material.icons.outlined.SportsMma
import androidx.compose.material.icons.outlined.SportsMotorsports
import androidx.compose.material.icons.outlined.SportsRugby
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.fitztv.sports.Sport

/** One line-icon family (Material Outlined) for the whole app. No emoji in the UI. */
object Glyph {
    val Star = Icons.Outlined.Star
    val ThumbUp = Icons.Outlined.ThumbUp
    val StarOff = Icons.Outlined.StarBorder
    val Bell = Icons.Outlined.Notifications
    val BellOn = Icons.Outlined.NotificationsActive
    val Record = Icons.Outlined.FiberManualRecord
    val Multiview = Icons.Outlined.GridView
    val Phone = Icons.Outlined.PhoneAndroid
    val Refresh = Icons.Outlined.Refresh
    val Search = Icons.Outlined.Search
    val Stats = Icons.Outlined.BarChart
    val Play = Icons.Outlined.PlayArrow
    val Pause = Icons.Outlined.Pause
    val Start = Icons.Outlined.SkipPrevious
    val GoLive = Icons.Outlined.SkipNext
    val Back = Icons.Outlined.FastRewind
    val Forward = Icons.Outlined.FastForward
    val Sound = Icons.AutoMirrored.Outlined.VolumeUp
    val Move = Icons.Outlined.OpenWith
    val Big = Icons.Outlined.OpenInFull
    val Swap = Icons.Outlined.SwapHoriz
    val NextStream = Icons.Outlined.Autorenew
    val Fullscreen = Icons.Outlined.Fullscreen
    val Remove = Icons.Outlined.Close
    val Scheduled = Icons.Outlined.Schedule
    val Done = Icons.Outlined.CheckCircle
    val Failed = Icons.Outlined.ErrorOutline
    val Games = Icons.Outlined.Event
    val Grid = Icons.Outlined.GridOn
    val Earlier = Icons.Outlined.ChevronLeft
    val Later = Icons.Outlined.ChevronRight
    val Tv = Icons.Outlined.LiveTv
    val RadioOn = Icons.Outlined.RadioButtonChecked
    val RadioOff = Icons.Outlined.RadioButtonUnchecked

    fun sport(s: Sport?): ImageVector = when (s) {
        Sport.NBA, Sport.NBL -> Icons.Outlined.SportsBasketball
        Sport.NFL -> Icons.Outlined.SportsFootball
        Sport.SOCCER -> Icons.Outlined.SportsSoccer
        Sport.CRICKET -> Icons.Outlined.SportsCricket
        Sport.AFL, Sport.NRL -> Icons.Outlined.SportsRugby
        Sport.NHL -> Icons.Outlined.SportsHockey
        Sport.MLB -> Icons.Outlined.SportsBaseball
        Sport.FIGHT -> Icons.Outlined.SportsMma
        Sport.MOTOR -> Icons.Outlined.SportsMotorsports
        Sport.TENNIS -> Icons.Outlined.SportsTennis
        Sport.GOLF -> Icons.Outlined.SportsGolf
        Sport.GENERAL, null -> Icons.Outlined.EmojiEvents
    }
}

/** Tabular figures so scores, clocks and times line up. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")
/** Game clock / scorebug time. */
val Clock = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
