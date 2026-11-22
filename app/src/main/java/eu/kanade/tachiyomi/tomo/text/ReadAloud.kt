package eu.kanade.tachiyomi.tomo.text

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Reads a fanfic chapter aloud with the phone's Spanish voice (RF-32), one block at a time so the reader can follow
 * the paragraph being read.
 */
class ReadAloud(context: Context) : UtteranceProgressListener() {

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** Index of the block being read. */
    private val _current = MutableStateFlow<Int?>(null)
    val current: StateFlow<Int?> = _current.asStateFlow()

    /** The last block was read; the reader moves on to the next chapter. */
    private val _finished =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    /** No Spanish voice installed; the reader explains where to get one. */
    private val _missingVoice = MutableStateFlow(false)
    val missingVoice: StateFlow<Boolean> = _missingVoice.asStateFlow()

    /** Set when the chapter ended while playing, so reading goes on in the next one. */
    var continueInNext = false

    private var ready = false
    private var pending: (() -> Unit)? = null
    private var lastId: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val result = engine().setLanguage(Locale.forLanguageTag("es"))
            _missingVoice.value = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
            engine().setOnUtteranceProgressListener(this)
            pending?.invoke()
        } else {
            _playing.value = false
        }
        pending = null
    }

    // The init callback can run before the property is assigned, so it reaches the engine through this
    private fun engine() = tts

    fun play(blocks: List<TextBlock>, from: Int) {
        _playing.value = true
        val start = {
            val parts = speakable(blocks, from)
            if (parts.isEmpty()) {
                _playing.value = false
            } else {
                lastId = parts.last().first
                parts.forEachIndexed { position, (id, text) ->
                    val mode = if (position == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    tts.speak(text, mode, null, id)
                }
            }
        }
        if (ready) start() else pending = start
    }

    fun pause() {
        continueInNext = false
        _playing.value = false
        pending = null
        if (ready) tts.stop()
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }

    override fun onStart(utteranceId: String) {
        _current.value = blockOf(utteranceId)
    }

    override fun onDone(utteranceId: String) {
        if (utteranceId == lastId && _playing.value) {
            _playing.value = false
            _current.value = null
            _finished.tryEmit(Unit)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String) {
        _playing.value = false
    }
}

/** Longest text a voice engine is sure to take in one go; longer paragraphs are read in parts. */
private const val MAX_PART = 3_900

/**
 * What is read from block [from] on, as (utterance id, text): scene breaks and the author's notes are skipped, and
 * the id "12:0" means block 12, part 0.
 */
internal fun speakable(blocks: List<TextBlock>, from: Int): List<Pair<String, String>> =
    blocks.withIndex().drop(from.coerceAtLeast(0))
        .filter { it.value.kind != TextBlock.Kind.BREAK && it.value.kind != TextBlock.Kind.NOTES }
        .filter { it.value.text.isNotBlank() }
        .flatMap { (index, block) -> block.text.chunked(MAX_PART).mapIndexed { part, text -> "$index:$part" to text } }

internal fun blockOf(utteranceId: String): Int? = utteranceId.substringBefore(':').toIntOrNull()
