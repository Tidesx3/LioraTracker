package app.liora.core.data.workout

import app.liora.core.domain.RestSound

/**
 * Plays the sound that ends a rest: when the rest alarm goes off, and as a preview when one is chosen in
 * Settings. The platform supplies it.
 */
fun interface RestSoundPlayer {
    /** Plays [sound] to its end; nothing for [RestSound.Off]. */
    suspend fun play(sound: RestSound)
}
