package tomo.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.image.BufferedImage
import java.io.File
import kotlin.time.Duration.Companion.hours

fun main() = application {
    val store = remember { SettingsHolder(SettingsStore.load()) }
    var visible by remember { mutableStateOf(true) }
    val icon = remember { BitmapPainter(tomoIcon().toComposeImageBitmap()) }
    val trayState = rememberTrayState()
    // Without a tray, closing the window must close Tomo, or it would be left running with no way back
    val hasTray = remember { SystemTray.isSupported() }

    if (hasTray) {
        Tray(
            icon = icon,
            state = trayState,
            tooltip = "Tomo",
            onAction = { visible = true },
            menu = {
                Item("Abrir Tomo", onClick = { visible = true })
                Item("Salir", onClick = ::exitApplication)
            },
        )
    }

    val windowState = rememberWindowState(width = 1100.dp, height = 760.dp)
    Window(
        onCloseRequest = { if (hasTray) visible = false else exitApplication() },
        visible = visible,
        title = "Tomo",
        icon = icon,
        state = windowState,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.F11) {
                windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Fullscreen
                }
                true
            } else {
                false
            }
        },
    ) {
        TomoTheme { App(store) }
    }

    // RF-19: the followed fanfics are checked every hour while Tomo runs, in the window or in the tray
    LaunchedEffect(Unit) {
        while (true) {
            try {
                checkNewChapters(store)?.let { trayState.sendNotification(Notification("Tomo", it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No network or the site's check: the next hour tries again
            }
            delay(1.hours)
        }
    }
}

/** Compares the chapters of the library's fanfics with the last check and says which have new ones. */
private suspend fun checkNewChapters(store: SettingsHolder): String? {
    val folder = store.settings.syncFolder?.let(::File) ?: return null
    val file = newestSyncFile(folder) ?: return null
    val fanfics = libraryOf(decodeSyncBackup(file.readBytes())).filter { it.isFanfic }
    val current = fanfics.associate { it.url to (it.title to Ffn.story(it.url).chapters.size) }
    val news = newChapters(current, store.settings.knownChapters)
    store.update { it.copy(knownChapters = it.knownChapters + current.mapValues { (_, value) -> value.second }) }
    if (news.isEmpty()) return null
    return news.joinToString("\n") { (title, count) ->
        if (count == 1) "Capítulo nuevo de $title" else "$count capítulos nuevos de $title"
    }
}

/** A gold "T" on the night background of the design, for the window and the tray. */
private fun tomoIcon(): BufferedImage {
    val size = 64
    return BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0x08, 0x0B, 0x14)
        g.fillRoundRect(0, 0, size, size, 18, 18)
        g.color = Color(0xF2, 0xC4, 0x6D)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(1, 1, size - 3, size - 3, 18, 18)
        g.font = Font(Font.SERIF, Font.BOLD, 44)
        val metrics = g.fontMetrics
        g.drawString("T", (size - metrics.stringWidth("T")) / 2, (size - metrics.height) / 2 + metrics.ascent)
        g.dispose()
    }
}
