package com.qingheng.weight.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun `new installs use the public HTTPS endpoint`() {
        assertEquals(DEFAULT_PERSONAL_SYNC_URL, resolvePersonalSyncUrl(null, migrationVersion = 0))
    }

    @Test
    fun `legacy Tailscale endpoint migrates to public HTTPS endpoint`() {
        assertEquals(
            DEFAULT_PERSONAL_SYNC_URL,
            resolvePersonalSyncUrl(LEGACY_PERSONAL_SYNC_URL, migrationVersion = 0),
        )
    }

    @Test
    fun `custom endpoint is preserved during migration`() {
        assertEquals(
            "https://sync.example.com/custom",
            resolvePersonalSyncUrl("https://sync.example.com/custom/", migrationVersion = 0),
        )
    }

    @Test
    fun `legacy endpoint can be selected manually after migration`() {
        assertEquals(
            LEGACY_PERSONAL_SYNC_URL,
            resolvePersonalSyncUrl(LEGACY_PERSONAL_SYNC_URL, migrationVersion = 1),
        )
    }
}
