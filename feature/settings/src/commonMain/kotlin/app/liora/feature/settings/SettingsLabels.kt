package app.liora.feature.settings

import androidx.compose.runtime.Composable
import app.liora.core.domain.OneRepMaxFormula
import app.liora.core.domain.RestSound
import app.liora.core.domain.ThemeMode
import app.liora.core.model.DistanceUnit
import app.liora.core.model.LengthUnit
import app.liora.core.model.WeightUnit
import app.liora.feature.settings.resources.Res
import app.liora.feature.settings.resources.formula_brzycki
import app.liora.feature.settings.resources.formula_brzycki_body
import app.liora.feature.settings.resources.formula_epley
import app.liora.feature.settings.resources.formula_epley_body
import app.liora.feature.settings.resources.language_de
import app.liora.feature.settings.resources.language_en
import app.liora.feature.settings.resources.language_system
import app.liora.feature.settings.resources.rest_sound_bell
import app.liora.feature.settings.resources.rest_sound_bell_body
import app.liora.feature.settings.resources.rest_sound_chime
import app.liora.feature.settings.resources.rest_sound_chime_body
import app.liora.feature.settings.resources.rest_sound_marimba
import app.liora.feature.settings.resources.rest_sound_marimba_body
import app.liora.feature.settings.resources.rest_sound_off
import app.liora.feature.settings.resources.rest_sound_off_body
import app.liora.feature.settings.resources.stall_weeks
import app.liora.feature.settings.resources.theme_dark
import app.liora.feature.settings.resources.theme_light
import app.liora.feature.settings.resources.theme_system
import app.liora.feature.settings.resources.unit_centimeters
import app.liora.feature.settings.resources.unit_inches
import app.liora.feature.settings.resources.unit_kilograms
import app.liora.feature.settings.resources.unit_kilometers
import app.liora.feature.settings.resources.unit_miles
import app.liora.feature.settings.resources.unit_pounds
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

// What each choice on the Settings screen is called.

@Composable
internal fun weightUnitName(unit: WeightUnit): String =
    stringResource(
        when (unit) {
            WeightUnit.Kilogram -> Res.string.unit_kilograms
            WeightUnit.Pound -> Res.string.unit_pounds
        },
    )

@Composable
internal fun distanceUnitName(unit: DistanceUnit): String =
    stringResource(
        when (unit) {
            DistanceUnit.Kilometer -> Res.string.unit_kilometers
            DistanceUnit.Mile -> Res.string.unit_miles
        },
    )

@Composable
internal fun bodyLengthUnitName(unit: LengthUnit): String =
    stringResource(if (unit == LengthUnit.Inch) Res.string.unit_inches else Res.string.unit_centimeters)

@Composable
internal fun formulaName(formula: OneRepMaxFormula): String =
    stringResource(
        when (formula) {
            OneRepMaxFormula.Epley -> Res.string.formula_epley
            OneRepMaxFormula.Brzycki -> Res.string.formula_brzycki
        },
    )

@Composable
internal fun formulaDescription(formula: OneRepMaxFormula): String =
    stringResource(
        when (formula) {
            OneRepMaxFormula.Epley -> Res.string.formula_epley_body
            OneRepMaxFormula.Brzycki -> Res.string.formula_brzycki_body
        },
    )

@Composable
internal fun stallText(window: Duration): String {
    val weeks = (window.inWholeDays / DAYS_PER_WEEK).toInt()
    return pluralStringResource(Res.plurals.stall_weeks, weeks, weeks)
}

@Composable
internal fun restSoundName(sound: RestSound): String =
    stringResource(
        when (sound) {
            RestSound.Chime -> Res.string.rest_sound_chime
            RestSound.Bell -> Res.string.rest_sound_bell
            RestSound.Marimba -> Res.string.rest_sound_marimba
            RestSound.Off -> Res.string.rest_sound_off
        },
    )

@Composable
internal fun restSoundDescription(sound: RestSound): String =
    stringResource(
        when (sound) {
            RestSound.Chime -> Res.string.rest_sound_chime_body
            RestSound.Bell -> Res.string.rest_sound_bell_body
            RestSound.Marimba -> Res.string.rest_sound_marimba_body
            RestSound.Off -> Res.string.rest_sound_off_body
        },
    )

@Composable
internal fun themeName(theme: ThemeMode): String =
    stringResource(
        when (theme) {
            ThemeMode.System -> Res.string.theme_system
            ThemeMode.Light -> Res.string.theme_light
            ThemeMode.Dark -> Res.string.theme_dark
        },
    )

@Composable
internal fun languageName(language: String?): String =
    stringResource(
        when (language) {
            null -> Res.string.language_system
            "de" -> Res.string.language_de
            else -> Res.string.language_en
        },
    )

private const val DAYS_PER_WEEK = 7
