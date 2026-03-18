package com.hacksecure.p2p.security

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

object SecureRandomProvider {

    private val secureRandomRef = AtomicReference<SecureRandom>()

    /**
     * Get global SecureRandom instance
     */
    fun get(): SecureRandom {

        var rng = secureRandomRef.get()

        if (rng == null) {
            rng = createSecureRandom()

            if (!secureRandomRef.compareAndSet(null, rng)) {
                rng = secureRandomRef.get()
            }
        }

        return rng
    }

    /**
     * Generate random bytes
     */
    fun nextBytes(length: Int): ByteArray {

        require(length > 0) { "Length must be positive" }

        val bytes = ByteArray(length)

        get().nextBytes(bytes)

        return bytes
    }

    /**
     * Fill existing byte array with random bytes
     */
    fun nextBytes(bytes: ByteArray) {
        get().nextBytes(bytes)
    }

    /**
     * Generate random integer
     */
    fun nextInt(): Int {
        return get().nextInt()
    }

    /**
     * Generate random long
     */
    fun nextLong(): Long {
        return get().nextLong()
    }

    /**
     * Generate random IV / nonce
     */
    fun generateNonce(length: Int = 12): ByteArray {
        return nextBytes(length)
    }

    /**
     * Create secure RNG instance
     */
    private fun createSecureRandom(): SecureRandom {

        val rng = SecureRandom()

        // Force initial seeding
        val seed = ByteArray(32)
        rng.nextBytes(seed)

        return rng
    }
}