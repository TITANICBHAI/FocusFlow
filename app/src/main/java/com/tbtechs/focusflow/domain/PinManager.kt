package com.tbtechs.focusflow.domain

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * PinManager
 *
 * Manages the defense PIN that gates settings changes in FocusFlow.
 *
 * ─── Storage format (v2 — new installs and migrated users) ───────────────────
 *
 *   PREF_V2_WRAPPED  ("defense_pin_v2_wrapped"):
 *       Base64-encoded [ 12-byte GCM IV | AES-GCM ciphertext of PBKDF2-derived key ]
 *
 *   PREF_V2_SALT  ("defense_pin_v2_salt"):
 *       Base64-encoded 16-byte PBKDF2 salt
 *
 *   The AES-GCM wrapping key lives entirely in the Android Keystore and never leaves
 *   the secure element. The PBKDF2-derived key is stored only in its Keystore-wrapped
 *   (encrypted) form. SharedPreferences never contains the raw PIN, the raw derived key,
 *   or the legacy SHA-256 hex digest.
 *
 * ─── Legacy migration (v1) ───────────────────────────────────────────────────
 *
 *   Existing users have their defense PIN stored as a plain SHA-256 hex digest at key
 *   "defense_pin_hash" — written by the React Native app's SharedPrefsModule / password-
 *   protection.tsx. The hash algorithm is SHA-256(UTF-8(pin)) → lowercase hex string
 *   (confirmed from src/utils/pinCrypto.ts's hashPassword() implementation).
 *
 *   Migration path (one-time, non-disruptive):
 *     1. verifyPin() detects a v1 hash when PREF_V2_WRAPPED is absent and
 *        PREF_LEGACY_HASH ("defense_pin_hash") contains a 64-char lowercase hex string.
 *     2. Computes SHA-256(UTF-8(candidate)) and compares constant-time.
 *     3. On match: immediately calls setPin(candidate), which writes PBKDF2+Keystore
 *        values and removes PREF_LEGACY_HASH. No PIN reset required for the user.
 *     4. On mismatch: returns false. Legacy hash remains untouched.
 *
 * ─── Logging ─────────────────────────────────────────────────────────────────
 *   No PIN, hash, salt, key material, or derived value is logged at any verbosity level.
 */
class PinManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        // SharedPreferences file shared with the enforcement services (matches
        // AppBlockerAccessibilityService.PREFS_NAME = "focusday_prefs").
        private const val PREFS_NAME = "focusday_prefs"

        // V2 storage keys (PBKDF2 + Keystore-wrapped)
        internal const val PREF_V2_WRAPPED  = "defense_pin_v2_wrapped"
        internal const val PREF_V2_SALT     = "defense_pin_v2_salt"

        // V1 legacy key written by the React Native layer.
        // Read once during migration; never written by PinManager.
        internal const val PREF_LEGACY_HASH = "defense_pin_hash"

        // Android Keystore alias for the AES-GCM wrapping key.
        private const val KEYSTORE_ALIAS = "focusflow_defense_pin_key"

        // PBKDF2 parameters.
        // None of these values appear in pinCrypto.ts or anywhere else in the source —
        // confirm all three before shipping.
        private const val PBKDF2_ITERATIONS = 310_000 // maybe — NIST SP 800-132 / OWASP 2023 recommendation; no value in source
        private const val PBKDF2_KEY_BITS   = 256     // maybe — not in source; 256-bit derived key
        private const val SALT_BYTES        = 16      // maybe — not in source; 128-bit salt

        // AES-GCM parameters — standard values, not sourced from app code.
        private const val GCM_IV_BYTES = 12  // maybe — NIST recommended 96-bit IV for GCM; not in source
        private const val GCM_TAG_BITS = 128 // maybe — standard 128-bit authentication tag; not in source
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns true if a defense PIN is currently configured (v1 or v2).
     */
    fun isPinSet(): Boolean {
        if (!prefs.getString(PREF_V2_WRAPPED, null).isNullOrBlank()) return true
        return isLegacyHash(prefs.getString(PREF_LEGACY_HASH, null))
    }

    /**
     * Stores [pin] using PBKDF2WithHmacSHA256, with the derived key wrapped by an
     * Android Keystore AES-GCM key. Removes any existing v1 legacy hash.
     *
     * Callers are responsible for ensuring the user already knows the current PIN
     * (or is setting one for the first time) before invoking this method.
     */
    fun setPin(pin: String) {
        val salt       = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derivedKey = deriveKey(pin, salt)
        val wrapped    = encryptWithKeystore(derivedKey)

        prefs.edit()
            .putString(PREF_V2_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(PREF_V2_SALT,    Base64.encodeToString(salt,    Base64.NO_WRAP))
            .remove(PREF_LEGACY_HASH)
            .apply()
    }

    /**
     * Removes the stored PIN entirely (both v1 and v2 keys).
     * Callers must verify the current PIN via [verifyPin] before calling this.
     */
    fun clearPin() {
        prefs.edit()
            .remove(PREF_V2_WRAPPED)
            .remove(PREF_V2_SALT)
            .remove(PREF_LEGACY_HASH)
            .apply()
    }

    /**
     * Returns true if [candidate] matches the stored defense PIN.
     *
     * Verification order:
     *   1. V2 (PBKDF2 + Keystore): decrypt stored wrapped key, re-derive from candidate,
     *      compare constant-time.
     *   2. V1 legacy (SHA-256 hex): compute SHA-256(UTF-8(candidate)), compare constant-time.
     *      On match, immediately migrates to v2 and removes the legacy hash.
     *   3. No PIN set: returns true (open access).
     *
     * Returns false on any cryptographic failure (fail-closed).
     */
    fun verifyPin(candidate: String): Boolean {
        // ── V2 path ──────────────────────────────────────────────────────────
        val v2Wrapped = prefs.getString(PREF_V2_WRAPPED, null)
        val v2Salt    = prefs.getString(PREF_V2_SALT,    null)

        if (!v2Wrapped.isNullOrBlank() && !v2Salt.isNullOrBlank()) {
            return try {
                val wrappedBytes = Base64.decode(v2Wrapped, Base64.NO_WRAP)
                val saltBytes    = Base64.decode(v2Salt,    Base64.NO_WRAP)
                val storedKey    = decryptWithKeystore(wrappedBytes)
                val candidateKey = deriveKey(candidate, saltBytes)
                MessageDigest.isEqual(storedKey, candidateKey)
            } catch (_: Exception) {
                // Keystore unavailable or data corruption — fail closed.
                false
            }
        }

        // ── V1 legacy SHA-256 path ────────────────────────────────────────────
        val legacyHash = prefs.getString(PREF_LEGACY_HASH, null)
        if (isLegacyHash(legacyHash)) {
            val candidateHash = computeLegacyHash(candidate)
            val storedHash    = legacyHash!!.lowercase()

            // Constant-time comparison on equal-length ASCII byte arrays.
            val matches = MessageDigest.isEqual(
                candidateHash.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8),
            )
            if (matches) {
                // ── One-time migration: upgrade to PBKDF2 + Keystore ─────────
                // setPin() writes v2 keys and calls remove(PREF_LEGACY_HASH).
                setPin(candidate)
            }
            return matches
        }

        // No PIN configured — open access.
        return true
    }

    // ─── Legacy detection ─────────────────────────────────────────────────────

    /**
     * A v1 hash is exactly 64 lowercase hex characters: SHA-256(UTF-8(pin)) as produced
     * by pinCrypto.ts's hashPassword(). No salt, no iterations, no prefix.
     */
    private fun isLegacyHash(value: String?): Boolean =
        value != null &&
        value.length == 64 &&
        value.all { it in '0'..'9' || it in 'a'..'f' }

    /**
     * Replicates pinCrypto.ts's hashPassword() exactly:
     *   UTF-8-encode the input → SHA-256 → lowercase hex (2 digits per byte).
     *
     * Used only during the one-time legacy migration verification. The result is
     * never stored or logged.
     */
    private fun computeLegacyHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ─── PBKDF2 ──────────────────────────────────────────────────────────────

    /**
     * Derives a 256-bit key from [pin] and [salt] using PBKDF2WithHmacSHA256.
     * The password char array is zeroed out in the PBEKeySpec before returning.
     */
    private fun deriveKey(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // ─── Android Keystore AES-GCM ─────────────────────────────────────────────

    /**
     * Returns the Keystore AES-256-GCM wrapping key, creating it on first call.
     * The key never leaves the Keystore; it is hardware-backed on supported devices.
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
        }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
    }

    /**
     * Encrypts [plaintext] with the Keystore AES-GCM key.
     * Returns: 12-byte random IV || ciphertext (with embedded 128-bit GCM tag).
     */
    private fun encryptWithKeystore(plaintext: ByteArray): ByteArray {
        val key    = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv         = cipher.iv          // Keystore generates a fresh random IV
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext              // prepend IV for storage alongside ciphertext
    }

    /**
     * Decrypts [ivPlusCiphertext] using the Keystore AES-GCM key.
     * Expects layout: 12-byte IV || ciphertext (with embedded 128-bit GCM tag).
     * Throws if GCM authentication fails (tampered ciphertext or wrong key).
     */
    private fun decryptWithKeystore(ivPlusCiphertext: ByteArray): ByteArray {
        val key        = getOrCreateKeystoreKey()
        val iv         = ivPlusCiphertext.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = ivPlusCiphertext.copyOfRange(GCM_IV_BYTES, ivPlusCiphertext.size)
        val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
