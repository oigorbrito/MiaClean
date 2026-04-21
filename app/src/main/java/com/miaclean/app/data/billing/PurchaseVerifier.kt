package com.miaclean.app.data.billing

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Client-side signature verification for Play Billing purchase payloads. This is the minimum
 * bar recommended by Google's Play Billing sample — a rooted user with Frida can still forge
 * a local `Purchase` object, but for the long-tail fleet it shuts down the trivial
 * "replay someone else's receipt" attack.
 *
 * For a higher assurance bar, verify on a backend using the Google Play Developer API so that
 * a forged client literally cannot tell the server "yes I paid" without a backing purchase
 * record. That's intentionally out of scope for this PR; see `PlayBillingRepository` TODO.
 *
 * The verifier is deliberately side-effect-free and pure-ish (aside from reading the Android
 * `Base64`/`Log` helpers) so it can be unit tested with fakes for `Base64` (Robolectric) or
 * by swapping in a JVM-native base64 decoder.
 */
class PurchaseVerifier(
    private val base64EncodedPublicKey: String,
    /**
     * Whether the host build is debuggable. Injected rather than read from [Build] so the
     * verifier stays unit-testable. Pass `BuildConfig.DEBUG` at the call site.
     *
     * In a release build with a blank key the constructor fails fast via [require] — shipping
     * a release APK without wiring `BILLING_PUBLIC_KEY` would otherwise silently accept every
     * forged purchase, which is strictly worse than a crash on startup.
     */
    private val isDebug: Boolean,
) {

    init {
        require(isDebug || base64EncodedPublicKey.isNotBlank()) {
            "BILLING_PUBLIC_KEY must be set for release builds. Configure it in " +
                "app/build.gradle.kts or override via -PBILLING_PUBLIC_KEY=..."
        }
    }

    /**
     * Returns `true` if [signature] is a valid RSA/SHA1 signature over [signedData] using the
     * configured [base64EncodedPublicKey]. Returns `false` (with a logged warning) on any
     * decoding, algorithm, or verification error — the caller should treat that as an
     * unverified purchase and NOT grant entitlement.
     *
     * When [base64EncodedPublicKey] is blank (only reachable in debug builds per the [init]
     * check above), verification is skipped and returns `true` so debug builds without a key
     * configured still flow end-to-end — with a loud warning so the missing key is noticeable.
     */
    fun verify(signedData: String, signature: String): Boolean {
        if (base64EncodedPublicKey.isBlank()) {
            Log.w(
                TAG,
                "PurchaseVerifier has no BILLING_PUBLIC_KEY configured; skipping signature " +
                    "verification. Debug build only — release builds fail fast in init().",
            )
            return true
        }
        return try {
            val publicKey = parsePublicKey(base64EncodedPublicKey)
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(signedData.toByteArray(Charsets.UTF_8))
            val decodedSig = Base64.decode(signature, Base64.DEFAULT)
            sig.verify(decodedSig)
        } catch (e: Exception) {
            Log.w(TAG, "Purchase signature verification failed", e)
            false
        }
    }

    private fun parsePublicKey(encodedKey: String): PublicKey {
        val decoded = Base64.decode(encodedKey, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(spec)
    }

    private companion object {
        const val TAG = "PurchaseVerifier"
        const val KEY_ALGORITHM = "RSA"
        const val SIGNATURE_ALGORITHM = "SHA1withRSA"
    }
}
