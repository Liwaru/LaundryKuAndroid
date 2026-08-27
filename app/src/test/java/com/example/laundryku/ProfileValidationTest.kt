package com.example.laundryku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun editProfileUsesLiveDatabaseLimits() {
        assertTrue(ProfileValidation.editErrors("Hendrik", "hendrik", "082388486205").isValid)
        assertEquals(InputError.TOO_LONG, ProfileValidation.editErrors("1234567890", "user", "0812").name)
        assertEquals(InputError.TOO_LONG, ProfileValidation.editErrors("Nama", "1234567890123", "0812").username)
        assertEquals(InputError.INVALID, ProfileValidation.editErrors("Nama", "user", "08A2").phone)
    }

    @Test
    fun passwordRequiresSixCharactersAndMatchingConfirmation() {
        val old = "o".repeat(7)
        val new = "n".repeat(7)
        assertEquals(InputError.TOO_SHORT, ProfileValidation.passwordErrors(old, "x".repeat(5), "x".repeat(5)).new)
        assertEquals(InputError.MISMATCH, ProfileValidation.passwordErrors(old, new, "z".repeat(7)).confirmation)
        assertEquals(InputError.SAME_AS_OLD, ProfileValidation.passwordErrors(old, old, old).new)
        assertTrue(ProfileValidation.passwordErrors(old, new, new).isValid)
        assertFalse(ProfileValidation.passwordErrors("", new, new).isValid)
    }
}
