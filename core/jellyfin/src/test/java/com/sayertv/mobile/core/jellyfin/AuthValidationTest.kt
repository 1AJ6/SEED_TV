/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests — no SDK, network, or mocks needed.
 * Version floor is an Ethan Sayer decision: Jellyfin >= 10.11.
 */
class AuthValidationTest {

    @Test fun `version 10_11_0 accepted`() = assertTrue(AuthValidation.meetsMinimumVersion("10.11.0"))
    @Test fun `version 10_11_5 accepted`() = assertTrue(AuthValidation.meetsMinimumVersion("10.11.5"))
    @Test fun `version 11_0_0 accepted`() = assertTrue(AuthValidation.meetsMinimumVersion("11.0.0"))
    @Test fun `version 10_10_7 rejected`() = assertFalse(AuthValidation.meetsMinimumVersion("10.10.7"))
    @Test fun `version 10_8_13 rejected`() = assertFalse(AuthValidation.meetsMinimumVersion("10.8.13"))
    @Test fun `garbage version rejected`() = assertFalse(AuthValidation.meetsMinimumVersion("dev"))

    @Test fun `bare host gets https scheme`() =
        assertEquals("https://demo.jellyfin.org", AuthValidation.normalizeUrl("demo.jellyfin.org"))

    @Test fun `trailing slash stripped`() =
        assertEquals("http://192.168.1.10:8096", AuthValidation.normalizeUrl("http://192.168.1.10:8096/"))

    @Test fun `whitespace trimmed`() =
        assertEquals("https://jf.example.com", AuthValidation.normalizeUrl("  https://jf.example.com  "))
}
