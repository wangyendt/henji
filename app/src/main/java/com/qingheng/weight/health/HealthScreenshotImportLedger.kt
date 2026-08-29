package com.qingheng.weight.health

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

/**
 * Remembers screenshots that have already made it all the way into the local database.
 *
 * A shared-content intent can remain the root intent of an Android task. Some devices replay
 * that intent when the task is restored, so clearing the in-memory Intent alone is not enough.
 * Hashing the bytes also handles providers that issue a fresh content Uri for the same image.
 */
class HealthScreenshotImportLedger(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    suspend fun fingerprint(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use(::sha256Hex)
            ?: throw IllegalArgumentException("无法打开运动详情分享图")
    }

    fun wasImported(fingerprint: String): Boolean =
        preferences.getStringSet(IMPORTED_SCREENSHOTS, emptySet()).orEmpty().contains(fingerprint)

    fun markImported(fingerprint: String) {
        val current = preferences.getStringSet(IMPORTED_SCREENSHOTS, emptySet()).orEmpty()
        preferences.edit().putStringSet(IMPORTED_SCREENSHOTS, current + fingerprint).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "health_screenshot_imports"
        private const val IMPORTED_SCREENSHOTS = "imported_sha256"
    }
}

internal fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
