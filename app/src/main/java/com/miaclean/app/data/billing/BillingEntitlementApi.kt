package com.miaclean.app.data.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.Purchase
import com.miaclean.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional server-side billing entitlement verifier.
 *
 * The backend endpoint is expected to validate purchase tokens using Google Play Developer API
 * and return whether the user should have Pro entitlement. If endpoint config is blank or the
 * request fails, callers should gracefully fall back to local billing-derived state.
 */
@Singleton
class BillingEntitlementApi @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun resolveIsPro(purchases: List<Purchase>, localIsPro: Boolean): Boolean? =
        withContext(Dispatchers.IO) {
            val endpoint = BuildConfig.BILLING_BACKEND_URL.trim()
            if (endpoint.isBlank()) return@withContext null

            val payload = JSONObject().apply {
                put("packageName", context.packageName)
                put("localIsPro", localIsPro)
                put(
                    "purchases",
                    JSONArray().apply {
                        purchases.forEach { purchase ->
                            put(
                                JSONObject().apply {
                                    put("purchaseToken", purchase.purchaseToken)
                                    put("orderId", purchase.orderId ?: JSONObject.NULL)
                                    put("purchaseState", purchase.purchaseState)
                                    put("isAcknowledged", purchase.isAcknowledged)
                                    put("purchaseTime", purchase.purchaseTime)
                                    put("products", JSONArray(purchase.products))
                                },
                            )
                        }
                    },
                )
            }

            var lastFailure: String? = null
            repeat(MAX_ATTEMPTS) { attempt ->
                val startedAt = System.currentTimeMillis()
                val attemptNumber = attempt + 1
                val result = runCatching {
                    postDecisionOnce(endpoint = endpoint, payload = payload.toString())
                }.getOrElse { error ->
                    lastFailure = "transport error: ${error.message}"
                    Log.w(TAG, "Server entitlement verify transport error (attempt $attemptNumber)", error)
                    null
                }

                if (result != null) {
                    if (result.httpCode in 200..299) {
                        if (result.body.isBlank()) {
                            lastFailure = "empty response body"
                        } else {
                            val decision = runCatching {
                                parseIsProFromResponse(result.body)
                            }.getOrElse { parseError ->
                                Log.w(TAG, "Server entitlement verify parse error", parseError)
                                null
                            }
                            if (decision != null) {
                                val latencyMs = System.currentTimeMillis() - startedAt
                                Log.i(
                                    TAG,
                                    "Server entitlement resolved=$decision " +
                                        "http=${result.httpCode} attempt=$attemptNumber " +
                                        "latencyMs=$latencyMs",
                                )
                                return@withContext decision
                            }
                            lastFailure = "unsupported response shape"
                        }
                    } else {
                        lastFailure = "http ${result.httpCode}"
                        Log.w(
                            TAG,
                            "Server entitlement verify failed (${result.httpCode}) attempt=$attemptNumber: ${result.body}",
                        )
                        if (result.httpCode !in 500..599) {
                            return@withContext null
                        }
                    }
                }

                if (attempt + 1 < MAX_ATTEMPTS) {
                    delay(RETRY_BACKOFF_MS)
                }
            }

            Log.w(TAG, "Server entitlement verify fallback to local decision ($lastFailure)")
            null
        }

    private fun postDecisionOnce(endpoint: String, payload: String): HttpResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 7_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }
            val body = (if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResult(httpCode = connection.responseCode, body = body)
        } finally {
            connection.disconnect()
        }
    }

    internal companion object {
        const val TAG = "BillingEntitlementApi"
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_BACKOFF_MS = 300L

        internal fun parseIsProFromResponse(body: String): Boolean? {
            val json = JSONObject(body)
            return when {
                json.has("isPro") -> json.optBoolean("isPro", false)
                json.optString("entitlement").equals("pro", ignoreCase = true) -> true
                json.optString("entitlement").equals("free", ignoreCase = true) -> false
                else -> null
            }
        }
    }

    private data class HttpResult(val httpCode: Int, val body: String)
}
