package app.liora.android.workout

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The haptics of the workout notification's buttons, which Compose's `LioraHaptics` can't reach: the
 * notification lives outside the app. Named by what happened, like `LioraHaptics`.
 *
 * They play as notification vibrations. Android drops touch vibrations from an app in the background,
 * which Liora is while a button in the shade runs; a notification vibration still comes, and silent
 * mode and the notification vibration setting turn it off.
 */
internal object WorkoutHaptics {
    /** −15 s or +15 s on the rest: one crisp click, to feel the tap landed with the phone in hand. */
    fun restAdjusted(context: Context) {
        val vibrator = context.vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val click =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(CLICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(click, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION") // VibrationAttributes from Android 13 on
            vibrator.vibrate(
                click,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
    }

    private fun Context.vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }

    /** Before Android 10 has predefined effects: about as long as a click feels. */
    private const val CLICK_MS = 20L
}
