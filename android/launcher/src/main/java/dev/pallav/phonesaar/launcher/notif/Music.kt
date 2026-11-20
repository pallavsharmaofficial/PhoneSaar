package dev.pallav.phonesaar.launcher

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MusicState(
    val active: Boolean = false, val title: String = "", val artist: String = "", val art: ImageBitmap? = null,
    val playing: Boolean = false, val durationMs: Long = 0, val positionMs: Long = 0, val stamp: Long = 0, val speed: Float = 1f,
    val likeAction: String? = null,
) {
    fun position(): Long {
        if (!playing) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - stamp) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p
    }
}

/** Follows the active media session through notification access; the notification listener is the pass for getActiveSessions. */
object MusicStore {
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state
    private var controller: MediaController? = null
    private var registered = false
    private var ctx: Context? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = rebuild()
        override fun onPlaybackStateChanged(state: PlaybackState?) = rebuild()
        override fun onSessionDestroyed() { controller = null; _state.value = MusicState() }
    }

    fun onSessionsMayHaveChanged(context: Context) {
        ctx = context.applicationContext
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val cn = NotifService.component(context)
        try {
            if (!registered) {
                msm.addOnActiveSessionsChangedListener({ pick(it.orEmpty()) }, cn)
                registered = true
            }
            pick(msm.getActiveSessions(cn))
        } catch (e: SecurityException) {
            _state.value = MusicState()
        }
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) { rebuild(); return }
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback)
        rebuild()
    }

    private fun rebuild() {
        val c = controller ?: run { _state.value = MusicState(); return }
        val md = c.metadata
        val ps = c.playbackState
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val like = ps?.customActions?.firstOrNull { a ->
            val s = (a.name?.toString().orEmpty() + " " + a.action).lowercase()
            listOf("like", "thumb", "heart", "favo", "favourite", "love", "save").any { it in s } && "unlike" !in s
        }?.action
        _state.value = MusicState(
            true, md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            art?.asImageBitmap(), ps?.state == PlaybackState.STATE_PLAYING,
            md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L, ps?.position ?: 0L, ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(), ps?.playbackSpeed ?: 1f, like,
        )
    }

    fun playPause() { val c = controller ?: return; if (_state.value.playing) c.transportControls.pause() else c.transportControls.play() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }
    fun seek(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun like() { val a = _state.value.likeAction ?: return; controller?.transportControls?.sendCustomAction(a, null) }
    fun refresh() { ctx?.let { onSessionsMayHaveChanged(it) } }
}
