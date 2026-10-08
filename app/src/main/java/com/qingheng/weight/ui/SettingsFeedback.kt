package com.qingheng.weight.ui

import kotlinx.coroutines.CancellationException

/** Report success only after the settings have actually been persisted. */
internal suspend fun saveSettingsWithFeedback(
    successMessage: String,
    notify: suspend (String) -> Unit,
    save: suspend () -> Unit,
): Boolean {
    try {
        save()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Storage errors may contain paths or credentials; keep the toast generic.
        notify("保存失败，请重试")
        return false
    }
    notify(successMessage)
    return true
}
