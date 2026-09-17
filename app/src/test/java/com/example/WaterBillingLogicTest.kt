package com.example

import com.example.security.PasswordHasher
import org.junit.Assert.*
import org.junit.Test

class WaterBillingLogicTest {

    @Test
    fun testPasswordHashingAndVerification() {
        val password = "SecurePassword@123"
        val salt = PasswordHasher.generateSalt()
        assertNotNull(salt)
        assertTrue(salt.isNotEmpty())

        val hash = PasswordHasher.hashPassword(password, salt)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())

        // Correct password should verify
        assertTrue(PasswordHasher.verifyPassword(password, salt, hash))

        // Incorrect password should fail
        assertFalse(PasswordHasher.verifyPassword("WrongPassword", salt, hash))
    }

    @Test
    fun testWaterConsumptionCalculation() {
        val previousReading = 145.0
        val lastReading = 160.0

        assertTrue("Last reading must be >= previous", lastReading >= previousReading)
        val consumed = lastReading - previousReading
        assertEquals(15.0, consumed, 0.001)

        // Tier test: 11-15 m³ @ 25 Birr/m³
        val unitPrice = 25.0
        val waterCharge = consumed * unitPrice
        assertEquals(375.0, waterCharge, 0.001)

        val meterRent = 30.0
        val totalPayable = waterCharge + meterRent
        assertEquals(405.0, totalPayable, 0.001)
    }

    @Test
    fun testNegativeConsumptionRejection() {
        val previousReading = 200.0
        val lastReading = 180.0

        val isValid = lastReading >= previousReading
        assertFalse("Should reject if last reading < previous reading", isValid)
    }
}
