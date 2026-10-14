package app.liora.android.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.liora.android.R
import app.liora.core.data.workout.RestSoundPlayer
import app.liora.core.domain.RestSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Plays the end-of-rest sounds (`res/raw`, made by `tools/sound/MakeSounds.java`) on the media stream.
 * With headphones in, it comes through them over the music. Without, it plays on the speaker, even with
 * the phone on vibrate, which mutes notification sounds. Music ducks for the moment where Android
 * allows it; since Android 15 that's only while Liora is on screen, and otherwise the sound plays over it.
 * With the screen off it plays only thanks to the workout's foreground service ([WorkoutForeground]): since
 * Android 17, Android mutes an app in the background without one, and says nothing.
 */
class AndroidRestSoundPlayer(
    private val context: Context,
) : RestSoundPlayer {
    private val audio = context.getSystemService(AudioManager::class.java)

    override suspend fun play(sound: RestSound) {
        val resource = sound.resource ?: return
        val focus =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(ATTRIBUTES)
                .build()
        val player = MediaPlayer()
        try {
            withContext(Dispatchers.IO) {
                player.setAudioAttributes(ATTRIBUTES)
                context.resources.openRawResourceFd(resource).use { player.setDataSource(it) }
                player.prepare()
            }
            audio.requestAudioFocus(focus)
            withTimeoutOrNull(LONGEST) {
                suspendCancellableCoroutine { done ->
                    player.setOnCompletionListener { if (done.isActive) done.resume(Unit) }
                    player.setOnErrorListener { _, _, _ ->
                        if (done.isActive) done.resume(Unit)
                        true
                    }
                    player.start()
                }
            }
        } catch (_: IOException) {
            // A sound that can't be read is skipped; the vibration still comes.
        } catch (_: IllegalArgumentException) {
            // Likewise for one the player won't take,
        } catch (_: IllegalStateException) {
            // or a player the system won't set up.
        } finally {
            player.release()
            audio.abandonAudioFocusRequest(focus)
        }
    }

    private val RestSound.resource: Int?
        get() =
            when (this) {
                RestSound.Chime -> R.raw.rest_chime
                RestSound.Bell -> R.raw.rest_bell
                RestSound.Marimba -> R.raw.rest_marimba
                RestSound.Off -> null
            }

    private companion object {
        val ATTRIBUTES: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        /** Longer than any of the sounds, in case the player never reports the end. */
        val LONGEST = 4.seconds
    }
}
