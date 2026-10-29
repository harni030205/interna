package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Enforces remote HTTPS backend authorization and payment verification whenever
 * INTERNA_BACKEND_URL is configured to an actual HTTPS backend service.
 *
 * Explicitly separates DIRECT_GEMINI_MODE (used solely for Gemini AI generation calls)
 * from administrator authorization, one-time provisioning, and payment approval.
 */
class BackendSecurityGatewayClient(
    private val backendUrlProvider: () -> String = {
        runCatching { BuildConfig.INTERNA_BACKEND_URL }.getOrDefault("")
    }
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DIRECT_GEMINI_MODE = "DIRECT_GEMINI_MODE"
    }

    fun rawConfiguredMode(): String = backendUrlProvider().trim()

    fun isRemoteHttpsBackendConfigured(): Boolean {
        val raw = rawConfiguredMode()
        return raw.startsWith("https://", ignoreCase = true)
    }

    fun validateBackendConfigurationSafety(): Result<Unit> = runCatching {
        val raw = rawConfiguredMode()
        if (raw.startsWith("http://", ignoreCase = true)) {
            error("Insecure cleartext INTERNA_BACKEND_URL (http://) is rejected. Configure an https:// URL or DIRECT_GEMINI_MODE.")
        }
    }

    suspend fun verifyInitialAdminProvisionOnBackendIfConfigured(
        email: String,
        suppliedProvisionToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            validateBackendConfigurationSafety().getOrThrow()
            if (!isRemoteHttpsBackendConfigured()) {
                return@runCatching
            }
            val baseUrl = rawConfiguredMode().trimEnd('/')
            val payload = JSONObject().apply {
                put("email", email)
                put("provisionToken", suppliedProvisionToken)
            }
            val request = Request.Builder()
                .url("$baseUrl/v1/admin/provision")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Remote backend rejected administrator provisioning (HTTP ${response.code}).")
                }
                if (body.isNotBlank()) {
                    val json = JSONObject(body)
                    if (json.has("authorized") && !json.optBoolean("authorized", false)) {
                        error(json.optString("message", "Administrator provisioning denied by backend."))
                    }
                }
            }
        }
    }

    suspend fun verifyAdminPaymentActionOnBackendIfConfigured(
        adminId: String,
        adminEmail: String,
        roleGrantSignature: String,
        requestId: String,
        decision: String,
        durationDays: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            validateBackendConfigurationSafety().getOrThrow()
            if (!isRemoteHttpsBackendConfigured()) {
                return@runCatching
            }
            val baseUrl = rawConfiguredMode().trimEnd('/')
            val payload = JSONObject().apply {
                put("adminId", adminId)
                put("adminEmail", adminEmail)
                put("requestId", requestId)
                put("decision", decision)
                put("durationDays", durationDays)
            }
            val request = Request.Builder()
                .url("$baseUrl/v1/admin/payments/decision")
                .header("Content-Type", "application/json")
                .header("X-Interna-Admin-Signature", roleGrantSignature)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Remote backend rejected payment decision '$decision' (HTTP ${response.code}).")
                }
            }
        }
    }
}
