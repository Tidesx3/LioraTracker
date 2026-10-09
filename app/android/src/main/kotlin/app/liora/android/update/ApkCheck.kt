package app.liora.android.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** What an APK, or the installed app, says about itself: enough to tell a genuine update. */
internal data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    /** Its signing certificates, as hex. */
    val signers: Set<String>,
)

/**
 * Whether a downloaded APK may install over Liora: the same app, a higher version, and signed with the
 * very same key. Android would refuse a different key anyway; checking first means nothing else is ever
 * handed to it, and the user hears why.
 */
internal object ApkCheck {
    fun isGenuineUpdate(
        candidate: ApkFacts,
        installed: ApkFacts,
    ): Boolean =
        candidate.packageName == installed.packageName &&
            candidate.versionCode > installed.versionCode &&
            candidate.signers.isNotEmpty() &&
            candidate.signers == installed.signers

    fun isGenuineUpdate(
        context: Context,
        apk: File,
    ): Boolean {
        val packages = context.packageManager
        val candidate = packages.getPackageArchiveInfo(apk.path, SIGNERS)?.facts() ?: return false
        val installed = packages.getPackageInfo(context.packageName, SIGNERS).facts()
        return isGenuineUpdate(candidate, installed)
    }

    private fun PackageInfo.facts() = ApkFacts(packageName, PackageInfoCompat.getLongVersionCode(this), signers())

    @Suppress("DEPRECATION") // signatures: the only way before Android 9, and for archives that leave signingInfo out
    private fun PackageInfo.signers(): Set<String> {
        val certificates: Array<Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && signingInfo != null) {
                signingInfo?.apkContentsSigners
            } else {
                signatures
            }
        return certificates.orEmpty().map { it.toCharsString() }.toSet()
    }

    @Suppress("DEPRECATION")
    private val SIGNERS =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
}
