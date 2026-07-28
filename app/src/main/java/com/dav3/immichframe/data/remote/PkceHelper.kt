package com.dav3.immichframe.data.remote

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PKCE (Proof Key for Code Exchange) helpers for the OAuth flow.
 *
 * PKCE prevents authorization-code interception attacks. The client generates
 * a [codeVerifier] (random string), sends its hash ([codeChallenge]) to the
 * server during authorize, then proves possession of the verifier during
 * callback. Immich's OAuth flow requires this.
 */
object PkceHelper {
    private const val VERIFIER_LENGTH = 64
    private const val RANDOM_BYTES = 32
    private val random = SecureRandom()

    /** Generate a high-entropy code verifier (43-128 chars, URL-safe). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_LENGTH)
        random.nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    /** S256 code challenge = BASE64URL(SHA256(codeVerifier)). */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return base64UrlEncode(digest)
    }

    /** Random state parameter to prevent CSRF in the OAuth flow. */
    fun generateState(): String {
        val bytes = ByteArray(RANDOM_BYTES)
        random.nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun base64UrlEncode(data: ByteArray): String = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
