package com.example.data.repository

import com.example.data.local.ApplicationSettingsEntity
import com.example.data.local.InternaDao
import com.example.data.local.PaymentMethodConfigEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.SubscriptionRequestEntity
import com.example.data.local.UsageRecordEntity
import com.example.domain.model.MobileMoneyMethod
import com.example.domain.model.PremiumPlanOption
import com.example.domain.model.isConfiguredForPayments
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class QuotaStatusSnapshot(
    val utcDate: String,
    val timezonePolicy: String = "UTC (Resets daily at 00:00 UTC)",
    val isPremiumActive: Boolean,
    val subscriptionStatus: String,
    val activePlanCode: String = "FREE_OFFLINE",
    val subscriptionDurationDays: Int = 0,
    val expirationDateMillis: Long = 0L,
    val freeTurnsUsed: Int,
    val freeTurnsLimit: Int,
    val remainingFreeTurns: Int,
    val premiumSecondsUsed: Long,
    val premiumSecondsLimit: Long,
    val remainingPremiumSeconds: Long,
    val maintenanceMode: Boolean
)

class QuotaAndSubscriptionRepository(
    private val dao: InternaDao,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val recentCallTimestampsByUser = ConcurrentHashMap<String, MutableList<Long>>()

    fun currentUtcDateString(nowMillis: Long = clockMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(nowMillis))
    }

    suspend fun getEffectiveQuotaSnapshot(userId: String): QuotaStatusSnapshot {
        val now = clockMillis()
        val utcDate = currentUtcDateString(now)
        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity()
        val user = dao.getUserById(userId)

        var subscription = dao.getSubscriptionForUser(userId)
        if (subscription != null && subscription.status == "APPROVED" && subscription.expirationDate <= now) {
            subscription = subscription.copy(
                status = "EXPIRED",
                planType = "FREE_OFFLINE",
                updatedAt = now
            )
            dao.upsertSubscription(subscription)
        }

        val isPremiumActive = subscription != null &&
            subscription.status == "APPROVED" &&
            PremiumPlanOption.isPremiumPlanCode(subscription.planType) &&
            subscription.expirationDate > now

        val pendingReq = dao.getPendingRequestForUser(userId)
        val effectiveStatus = when {
            isPremiumActive -> "APPROVED"
            pendingReq != null -> "PENDING"
            subscription != null -> subscription.status
            else -> "NONE"
        }

        val durationDays = if (subscription != null && subscription.startDate > 0L && subscription.expirationDate > subscription.startDate) {
            ((subscription.expirationDate - subscription.startDate) / 86_400_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }

        val usage = dao.getUsageRecord("${userId}_$utcDate")
            ?: UsageRecordEntity(id = "${userId}_$utcDate", userId = userId, utcDate = utcDate)

        val freeLimit = user?.customDailyAllowanceOverride ?: settings.freeOfflineDailyTurnsLimit
        val premiumMinutesLimit = user?.customDailyAllowanceOverride
            ?: (subscription?.dailyMinutesAllowance ?: settings.premiumOnlineDailyMinutesLimit)
        val premiumSecondsLimit = premiumMinutesLimit.toLong() * 60L

        return QuotaStatusSnapshot(
            utcDate = utcDate,
            isPremiumActive = isPremiumActive,
            subscriptionStatus = effectiveStatus,
            activePlanCode = if (isPremiumActive) subscription.planType else "FREE_OFFLINE",
            subscriptionDurationDays = durationDays,
            expirationDateMillis = subscription?.expirationDate ?: 0L,
            freeTurnsUsed = usage.freeTurnsUsed,
            freeTurnsLimit = freeLimit,
            remainingFreeTurns = (freeLimit - usage.freeTurnsUsed).coerceAtLeast(0),
            premiumSecondsUsed = usage.premiumSecondsUsed,
            premiumSecondsLimit = premiumSecondsLimit,
            remainingPremiumSeconds = (premiumSecondsLimit - usage.premiumSecondsUsed).coerceAtLeast(0L),
            maintenanceMode = settings.maintenanceMode
        )
    }

    suspend fun checkCanExecuteConversationTurn(userId: String): Result<QuotaStatusSnapshot> = runCatching {
        val now = clockMillis()
        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity()
        if (settings.maintenanceMode) {
            error("Interna AI is temporarily in scheduled maintenance mode. Offline study tools remain available.")
        }
        if (!settings.aiConversationEnabled) {
            error("AI Conversation is currently disabled by platform policy.")
        }

        val user = dao.getUserById(userId) ?: error("User session invalid.")
        if (user.accountStatus != "ACTIVE") {
            error("Account is ${user.accountStatus.lowercase()}. Access to AI features is restricted.")
        }

        val timestamps = recentCallTimestampsByUser.getOrPut(userId) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { now - it > 60_000L }
            if (timestamps.size >= settings.rateLimitRequestsPerMinute) {
                error("Rate limit reached (${settings.rateLimitRequestsPerMinute} requests/min). Please pause briefly.")
            }
            timestamps.add(now)
        }

        val snapshot = getEffectiveQuotaSnapshot(userId)
        if (snapshot.isPremiumActive) {
            if (snapshot.remainingPremiumSeconds <= 0L) {
                error("Daily Premium AI conversation allowance reached (${snapshot.premiumSecondsLimit / 60} mins). Quota resets at 00:00 UTC.")
            }
        } else {
            if (snapshot.remainingFreeTurns <= 0) {
                error("Daily Free AI conversation limit reached (${snapshot.freeTurnsLimit} turns/day). Quota resets at 00:00 UTC, or you may submit a verified Premium plan payment.")
            }
        }
        snapshot
    }

    suspend fun recordConversationUsage(
        userId: String,
        elapsedActiveSeconds: Long,
        estimatedTokens: Long,
        succeeded: Boolean
    ) {
        val now = clockMillis()
        val utcDate = currentUtcDateString(now)
        val recordId = "${userId}_$utcDate"
        val current = dao.getUsageRecord(recordId)
            ?: UsageRecordEntity(id = recordId, userId = userId, utcDate = utcDate)

        val snapshot = getEffectiveQuotaSnapshot(userId)
        val boundedSeconds = elapsedActiveSeconds.coerceIn(5L, 300L)

        val updated = if (succeeded) {
            if (snapshot.isPremiumActive) {
                current.copy(
                    premiumSecondsUsed = current.premiumSecondsUsed + boundedSeconds,
                    aiRequestsCount = current.aiRequestsCount + 1,
                    estimatedTokensUsed = current.estimatedTokensUsed + estimatedTokens,
                    updatedAt = now
                )
            } else {
                current.copy(
                    freeTurnsUsed = current.freeTurnsUsed + 1,
                    aiRequestsCount = current.aiRequestsCount + 1,
                    estimatedTokensUsed = current.estimatedTokensUsed + estimatedTokens,
                    updatedAt = now
                )
            }
        } else {
            current.copy(
                apiErrorCount = current.apiErrorCount + 1,
                updatedAt = now
            )
        }
        dao.upsertUsageRecord(updated)
    }

    /**
     * Submits a mobile-money payment verification request for Yas Money, Airtel Money, or Orange Money.
     *
     * Security & anti-fraud guarantees:
     * - Requires the selected mobile-money method to be enabled and configured by the developer.
     * - Rejects duplicate/replayed transaction references across all requests.
     * - Validates amount against configured plan price and validates YYYY-MM-DD payment date.
     * - Always stores as PENDING (manual verification); entering a reference or proof never unlocks Premium.
     */
    suspend fun submitMobileMoneyPaymentRequest(
        userId: String,
        requestedPlan: String,
        paymentMethod: String,
        transactionReference: String,
        amountPaid: String,
        paymentDate: String,
        proofOfPaymentUri: String = "",
        optionalMessage: String = ""
    ): Result<SubscriptionRequestEntity> = runCatching {
        val now = clockMillis()
        val user = dao.getUserById(userId) ?: error("User account not found.")
        require(user.accountStatus == "ACTIVE") { "Suspended accounts cannot submit payment requests." }

        val existingPending = dao.getPendingRequestForUser(userId)
        if (existingPending != null) {
            error("You already have a pending payment request (${existingPending.id}) awaiting administrator verification.")
        }

        val activeSub = dao.getSubscriptionForUser(userId)
        if (activeSub != null && activeSub.status == "APPROVED" && activeSub.expirationDate > now) {
            error("Your Premium subscription is already active.")
        }

        val method = MobileMoneyMethod.fromCode(paymentMethod)
            ?: error("Please select a supported mobile-money method: Yas Money, Airtel Money, or Orange Money.")

        val methodConfig = dao.getPaymentMethodConfig(method.code)
        require(methodConfig != null && methodConfig.isConfiguredForPayments()) {
            "${method.displayName} payment receiving details have not been configured by the administrator yet."
        }

        val planOption = PremiumPlanOption.fromPlanCode(requestedPlan)
            ?: error("Please select a valid Premium plan (Monthly, Quarterly, or Yearly).")

        val expectedPriceRaw = planOption.configuredPrice(methodConfig)
        require(expectedPriceRaw.isNotBlank()) {
            "Price for ${planOption.title} via ${method.displayName} has not been configured by the administrator yet."
        }

        val cleanAmount = amountPaid.trim().replace(",", "").replace(" ", "")
        val parsedAmount = cleanAmount.toDoubleOrNull()
        require(parsedAmount != null && parsedAmount > 0.0) {
            "Enter a valid positive payment amount."
        }
        val expectedNumeric = expectedPriceRaw.replace(",", "").replace(" ", "").toDoubleOrNull()
        if (expectedNumeric != null && expectedNumeric > 0.0) {
            require(parsedAmount >= expectedNumeric) {
                "Submitted amount ($cleanAmount ${methodConfig.currency}) is less than the required plan price ($expectedPriceRaw ${methodConfig.currency})."
            }
        }

        val cleanTxRef = transactionReference.trim()
        require(cleanTxRef.length in 4..80) {
            "Enter a valid mobile-money transaction reference (4–80 characters)."
        }
        val normalizedTxRef = cleanTxRef.uppercase().replace(Regex("\\s+"), "")
        val duplicateTx = dao.findAnyRequestByNormalizedTxRef(normalizedTxRef)
        require(duplicateTx == null) {
            "This transaction reference has already been submitted. Duplicate transaction references are blocked."
        }

        val cleanDate = paymentDate.trim()
        validatePaymentDate(cleanDate, now)

        val request = SubscriptionRequestEntity(
            id = "payreq_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            userId = user.id,
            userEmail = user.email,
            userName = user.fullName,
            requestedPlan = planOption.planCode,
            optionalMessage = optionalMessage.trim().take(400),
            status = "PENDING", // Strictly Pending until verified & approved by administrator
            durationDays = planOption.durationDays,
            createdAt = now,
            paymentMethod = method.code,
            transactionReference = cleanTxRef,
            normalizedTxRef = normalizedTxRef,
            amountPaid = cleanAmount,
            currency = methodConfig.currency,
            paymentDate = cleanDate,
            proofOfPaymentUri = proofOfPaymentUri.trim().take(500),
            verificationMode = "MANUAL_VERIFICATION"
        )
        dao.insertSubscriptionRequest(request)
        request
    }

    /**
     * Legacy-compatible helper for general manual subscription requests when no payment method is specified.
     * Remains strictly PENDING until approved by an authorized administrator.
     */
    suspend fun submitSubscriptionRequest(
        userId: String,
        requestedPlan: String,
        optionalMessage: String
    ): Result<SubscriptionRequestEntity> = runCatching {
        val now = clockMillis()
        val user = dao.getUserById(userId) ?: error("User account not found.")
        require(user.accountStatus == "ACTIVE") { "Suspended accounts cannot submit subscription requests." }

        val existingPending = dao.getPendingRequestForUser(userId)
        require(existingPending == null) { "You already have a pending subscription request awaiting administrator review." }

        val activeSub = dao.getSubscriptionForUser(userId)
        if (activeSub != null && activeSub.status == "APPROVED" && activeSub.expirationDate > now) {
            error("Your Premium Online subscription is already active.")
        }

        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity()
        val planCode = requestedPlan.ifBlank { "PREMIUM_ONLINE" }
        val duration = PremiumPlanOption.defaultDurationDaysFor(planCode, settings.defaultSubscriptionDurationDays)

        val request = SubscriptionRequestEntity(
            id = "req_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            userId = user.id,
            userEmail = user.email,
            userName = user.fullName,
            requestedPlan = planCode,
            optionalMessage = optionalMessage.trim().take(400),
            status = "PENDING",
            durationDays = duration,
            createdAt = now,
            verificationMode = "MANUAL_VERIFICATION"
        )
        dao.insertSubscriptionRequest(request)
        request
    }

    private fun validatePaymentDate(dateStr: String, nowMillis: Long) {
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateStr)) {
            "Enter payment date in YYYY-MM-DD format (e.g., 2026-10-29)."
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val parsed = runCatching { sdf.parse(dateStr) }.getOrNull()
            ?: error("Payment date '$dateStr' is not a valid calendar date.")
        val maxFutureAllowanceMs = nowMillis + 16L * 3600L * 1000L // UTC+14 tolerance
        require(parsed.time <= maxFutureAllowanceMs) {
            "Payment date cannot be in the future."
        }
    }

    fun observePaymentMethodConfigs(): Flow<List<PaymentMethodConfigEntity>> =
        dao.observePaymentMethodConfigs()

    fun observeUserSubscription(userId: String): Flow<SubscriptionEntity?> =
        dao.observeSubscriptionForUser(userId)

    fun observeUserRequests(userId: String): Flow<List<SubscriptionRequestEntity>> =
        dao.observeUserSubscriptionRequests(userId)
}
