package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GuardPreferences
import com.example.data.model.LockReason
import com.example.data.model.WatchMode
import com.example.data.security.PinCryptoHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KidShieldSecurityTest {

    @Test
    fun `pin hashing and verification requires exact 8-digit match`() {
        val pin = "48291057"
        val (hash, salt) = PinCryptoHelper.hashPin(pin)

        assertTrue(PinCryptoHelper.verifyPin(pin, hash, salt))
        assertFalse(PinCryptoHelper.verifyPin("48291058", hash, salt))
        assertFalse(PinCryptoHelper.verifyPin("4829105", hash, salt))
    }

    @Test
    fun `masked visual digest never exposes plaintext digits and changes with nonce`() {
        val nonceA = PinCryptoHelper.generateSessionNonce()
        val nonceB = PinCryptoHelper.generateSessionNonce()
        val digestA = PinCryptoHelper.computeMaskedVisualDigest("1234", nonceA)
        val digestB = PinCryptoHelper.computeMaskedVisualDigest("1234", nonceB)

        assertFalse(digestA.contains("1234"))
        assertNotEquals(digestA, digestB)
        assertEquals((0..9).toSet(), PinCryptoHelper.generateShuffledDigits().toSet())
    }

    @Test
    fun `default mode is kids and lockout persists across preference re-instantiation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs1 = GuardPreferences(context)
        assertEquals(WatchMode.KIDS, prefs1.activeMode)
        assertEquals(3600, prefs1.kidsRemainingSeconds)

        // Simulate 1-hour limit reached and device rebooting
        prefs1.kidsRemainingSeconds = 0
        prefs1.isKidsLockedOut = true
        prefs1.lockReason = LockReason.ONE_HOUR_LIMIT

        val prefs2 = GuardPreferences(context)
        assertEquals(WatchMode.KIDS, prefs2.activeMode)
        assertEquals(0, prefs2.kidsRemainingSeconds)
        assertTrue(prefs2.isKidsLockedOut)
        assertEquals(LockReason.ONE_HOUR_LIMIT, prefs2.lockReason)
    }
}
