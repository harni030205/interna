package com.example.data.repository

import com.example.data.local.AdminAuditLogEntity
import com.example.data.local.ApplicationSettingsEntity
import com.example.data.local.InternaDao
import com.example.data.local.NotificationEntity
import com.example.data.local.PaymentMethodConfigEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.SurveyEntity
import com.example.data.local.UsageRecordEntity
import com.example.data.local.UserEntity
import com.example.data.remote.BackendSecurityGatewayClient
import com.example.domain.model.MobileMoneyMethod
import com.example.domain.model.PremiumPlanOption
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AdminOverviewMetrics(
    val totalRegisteredUsers: Int,
    val newUsersLast7Days: Int,
    val dailyActiveUsers: Int,
    val weeklyActiveUsers: Int,
    val monthlyActiveUsers: Int,
    val activePremiumSubscriptions: Int,
    val pendingSubscriptionRequests: Int,
    val approvedSubscriptionRequests: Int,
    val rejectedSubscriptionRequests: Int,
    val totalAiConversationRequests: Int,
    val usageLimitWarningsToday: Int,
    val totalApiErrors: Int,
    val estimatedTotalTokens: Long,
    val billingIntegrationStatus: String = "Manual Mobile-Money Verification Active (Yas Money, Airtel Money, Orange Money) — Transfers stay Pending until verified by administrator."
)

data class AdminUserSummaryRow(
    val user: UserEntity,
    val subscription: SubscriptionEntity?,
    val totalTurnsUsed: Int,
    val totalPremiumSecondsUsed: Long,
    val totalXpEarned: Int
)

class AdminConsoleRepository(
    private val dao: InternaDao,
    private val authRepo: AuthAndUserRepository,
    private val quotaRepo: QuotaAndSubscriptionRepository,
    private val backendGateway: BackendSecurityGatewayClient = BackendSecurityGatewayClient(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private suspend fun requireAdminUser(actingAdminId: String): UserEntity {
        backendGateway.validateBackendConfigurationSafety().getOrThrow()
        val user = dao.getUserById(actingAdminId)
        val isAuthorized = user != null && authRepo.isVerifiedAdministrator(actingAdminId)
        if (!isAuthorized) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = actingAdminId,
                    adminEmail = user?.email ?: "unauthenticated",
                    actionType = "UNAUTHORIZED_ADMIN_ACCESS_BLOCKED",
                    affectedTarget = "DEVELOPER_CONSOLE",
                    sanitizedSummary = "Blocked unauthorized attempt to invoke protected administrative operation.",
                    succeeded = false,
                    timestamp = clockMillis()
                )
            )
            throw SecurityException("Access denied: Verified administrator privileges are required.")
        }
        return user!!
    }

    private suspend fun recordAudit(
        admin: UserEntity,
        actionType: String,
        target: String,
        summary: String,
        succeeded: Boolean
    ) {
        dao.insertAuditLog(
            AdminAuditLogEntity(
                id = "aud_${UUID.randomUUID()}",
                adminId = admin.id,
                adminEmail = admin.email,
                actionType = actionType,
                affectedTarget = target,
                sanitizedSummary = summary.take(300),
                succeeded = succeeded,
                timestamp = clockMillis()
            )
        )
    }

    suspend fun getOverviewMetrics(actingAdminId: String): Result<AdminOverviewMetrics> = runCatching {
        requireAdminUser(actingAdminId)
        val now = clockMillis()
        val dayMs = 24L * 60L * 60L * 1000L
        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity()
        val utcToday = quotaRepo.currentUtcDateString(now)

        AdminOverviewMetrics(
            totalRegisteredUsers = dao.countTotalUsers(),
            newUsersLast7Days = dao.countNewUsersSince(now - 7L * dayMs),
            dailyActiveUsers = dao.countActiveUsersSince(now - dayMs),
            weeklyActiveUsers = dao.countActiveUsersSince(now - 7L * dayMs),
            monthlyActiveUsers = dao.countActiveUsersSince(now - 30L * dayMs),
            activePremiumSubscriptions = dao.countActivePremiumSubscriptions(now),
            pendingSubscriptionRequests = dao.countRequestsByStatus("PENDING"),
            approvedSubscriptionRequests = dao.countRequestsByStatus("APPROVED"),
            rejectedSubscriptionRequests = dao.countRequestsByStatus("REJECTED"),
            totalAiConversationRequests = dao.sumGlobalAiRequests(),
            usageLimitWarningsToday = dao.countUsageLimitWarningsToday(
                utcDate = utcToday,
                warnTurnsThreshold = (settings.freeOfflineDailyTurnsLimit * 0.8).toInt().coerceAtLeast(1),
                warnSecondsThreshold = (settings.premiumOnlineDailyMinutesLimit * 60L * 80L) / 100L
            ),
            totalApiErrors = dao.sumGlobalApiErrors(),
            estimatedTotalTokens = dao.sumGlobalEstimatedTokens()
        )
    }

    suspend fun searchUsers(
        actingAdminId: String,
        query: String,
        statusFilter: String,
        page: Int,
        pageSize: Int = 15
    ): Result<List<AdminUserSummaryRow>> = runCatching {
        requireAdminUser(actingAdminId)
        val users = dao.searchUsersPaginated(
            searchQuery = query.trim(),
            statusFilter = statusFilter,
            limit = pageSize,
            offset = (page.coerceAtLeast(0)) * pageSize
        )
        users.map { u ->
            AdminUserSummaryRow(
                user = u,
                subscription = dao.getSubscriptionForUser(u.id),
                totalTurnsUsed = dao.sumUserTotalTurns(u.id),
                totalPremiumSecondsUsed = dao.sumUserTotalPremiumSeconds(u.id),
                totalXpEarned = dao.sumUserTotalXp(u.id)
            )
        }
    }

    /**
     * Securely configures receiving account number, account name, currency, plan prices,
     * and transfer instructions for Yas Money, Airtel Money, or Orange Money.
     */
    suspend fun updatePaymentMethodConfig(
        actingAdminId: String,
        methodCode: String,
        isEnabled: Boolean,
        receivingAccountNumber: String,
        receivingAccountName: String,
        currency: String,
        monthlyPrice: String,
        quarterlyPrice: String,
        yearlyPrice: String,
        transferInstructions: String
    ): Result<PaymentMethodConfigEntity> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        val method = MobileMoneyMethod.fromCode(methodCode)
            ?: error("Unsupported payment method code: $methodCode")

        val cleanCurrency = currency.trim().uppercase().ifBlank { "MGA" }
        require(cleanCurrency.length in 2..10) { "Currency code must be 2–10 characters (e.g. MGA, USD, EUR)." }

        listOf("Monthly" to monthlyPrice, "Quarterly" to quarterlyPrice, "Yearly" to yearlyPrice).forEach { (label, raw) ->
            val trimmed = raw.trim().replace(",", "").replace(" ", "")
            if (trimmed.isNotBlank()) {
                val value = trimmed.toDoubleOrNull()
                require(value != null && value > 0.0) { "$label price must be a positive number." }
            }
        }

        val now = clockMillis()
        val updated = PaymentMethodConfigEntity(
            methodCode = method.code,
            displayName = method.displayName,
            isEnabled = isEnabled,
            receivingAccountNumber = receivingAccountNumber.trim().take(60),
            receivingAccountName = receivingAccountName.trim().take(100),
            currency = cleanCurrency,
            monthlyPrice = monthlyPrice.trim().take(30),
            quarterlyPrice = quarterlyPrice.trim().take(30),
            yearlyPrice = yearlyPrice.trim().take(30),
            transferInstructions = transferInstructions.trim().take(500),
            updatedByAdminId = admin.id,
            updatedAt = now
        )
        dao.upsertPaymentMethodConfig(updated)
        recordAudit(
            admin = admin,
            actionType = "PAYMENT_METHOD_CONFIG_UPDATED",
            target = method.code,
            summary = "Updated ${method.displayName} config (enabled=$isEnabled, accountName='${updated.receivingAccountName}', currency=$cleanCurrency).",
            succeeded = true
        )
        updated
    }

    suspend fun approveSubscriptionRequest(
        actingAdminId: String,
        requestId: String,
        durationDays: Int = 0,
        dailyMinutesAllowance: Int = 120,
        renewalPolicy: String = "MANUAL_RENEWAL"
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        val existingReq = dao.getSubscriptionRequestById(requestId)
            ?: error("Payment/subscription request not found.")

        val resolvedDurationDays = if (durationDays > 0) {
            durationDays
        } else {
            PremiumPlanOption.defaultDurationDaysFor(existingReq.requestedPlan, existingReq.durationDays.coerceAtLeast(30))
        }
        require(resolvedDurationDays in 1..3650) { "Subscription duration must be between 1 and 3650 days." }
        require(dailyMinutesAllowance in 5..1440) { "Daily allowance must be between 5 and 1440 minutes." }

        val roleGrant = dao.getRolePermission(admin.id)?.grantSignature.orEmpty()
        backendGateway.verifyAdminPaymentActionOnBackendIfConfigured(
            adminId = admin.id,
            adminEmail = admin.email,
            roleGrantSignature = roleGrant,
            requestId = requestId,
            decision = "APPROVE",
            durationDays = resolvedDurationDays
        ).getOrThrow()

        val approved = dao.approveSubscriptionRequestAtomically(
            requestId = requestId,
            adminId = admin.id,
            durationDays = resolvedDurationDays,
            dailyMinutesAllowance = dailyMinutesAllowance,
            renewalPolicy = renewalPolicy,
            nowMillis = clockMillis()
        )
        if (!approved) {
            recordAudit(admin, "SUBSCRIPTION_APPROVE_REJECTED_REPLAY", requestId, "Request not pending or already processed.", false)
            error("Payment/subscription request is no longer pending or was already processed.")
        }

        val methodSummary = if (existingReq.paymentMethod.isNotBlank()) {
            "${MobileMoneyMethod.displayLabel(existingReq.paymentMethod)} ref=${existingReq.transactionReference} (${existingReq.amountPaid} ${existingReq.currency})"
        } else {
            "manual plan request"
        }
        recordAudit(
            admin = admin,
            actionType = "SUBSCRIPTION_APPROVED",
            target = requestId,
            summary = "Verified $methodSummary & activated ${existingReq.requestedPlan} for $resolvedDurationDays days ($dailyMinutesAllowance mins/day).",
            succeeded = true
        )
    }

    suspend fun rejectSubscriptionRequest(
        actingAdminId: String,
        requestId: String,
        rejectionReason: String
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        val req = dao.getSubscriptionRequestById(requestId) ?: error("Request not found.")
        require(req.status == "PENDING") { "Only pending requests can be rejected." }

        val roleGrant = dao.getRolePermission(admin.id)?.grantSignature.orEmpty()
        backendGateway.verifyAdminPaymentActionOnBackendIfConfigured(
            adminId = admin.id,
            adminEmail = admin.email,
            roleGrantSignature = roleGrant,
            requestId = requestId,
            decision = "REJECT",
            durationDays = 0
        ).getOrThrow()

        val now = clockMillis()
        val cleanReason = rejectionReason.trim().take(300)
        dao.updateSubscriptionRequest(
            req.copy(
                status = "REJECTED",
                rejectionReason = cleanReason,
                reviewedByAdminId = admin.id,
                reviewedAt = now
            )
        )
        dao.insertNotification(
            NotificationEntity(
                id = "notif_reject_$requestId",
                userId = req.userId,
                title = "Payment Verification Update",
                body = if (cleanReason.isNotBlank()) {
                    "Your ${req.requestedPlan} payment request was not approved: $cleanReason. Free access remains active."
                } else {
                    "Your ${req.requestedPlan} payment request could not be verified. Free access remains active."
                },
                type = "SUBSCRIPTION",
                createdAt = now
            )
        )
        recordAudit(
            admin = admin,
            actionType = "SUBSCRIPTION_REJECTED",
            target = requestId,
            summary = "Rejected payment/subscription request for ${req.userEmail} (${MobileMoneyMethod.displayLabel(req.paymentMethod)} ref=${req.transactionReference}).",
            succeeded = true
        )
    }

    suspend fun setUserSuspensionStatus(
        actingAdminId: String,
        targetUserId: String,
        suspend: Boolean
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        require(admin.id != targetUserId) { "Administrators cannot suspend their own active session." }
        val target = dao.getUserById(targetUserId) ?: error("Target user not found.")
        val newStatus = if (suspend) "SUSPENDED" else "ACTIVE"
        dao.updateUser(target.copy(accountStatus = newStatus))
        recordAudit(
            admin = admin,
            actionType = if (suspend) "USER_SUSPENDED" else "USER_RESTORED",
            target = target.email,
            summary = "Changed account status to $newStatus.",
            succeeded = true
        )
    }

    suspend fun setUserCustomDailyAllowance(
        actingAdminId: String,
        targetUserId: String,
        customLimit: Int?,
        internalNote: String
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        if (customLimit != null) {
            require(customLimit in 1..1440) { "Custom daily limit must be between 1 and 1440." }
        }
        val target = dao.getUserById(targetUserId) ?: error("Target user not found.")
        dao.updateUser(
            target.copy(
                customDailyAllowanceOverride = customLimit,
                adminInternalNote = internalNote.trim().take(400)
            )
        )
        recordAudit(
            admin = admin,
            actionType = "USER_LIMIT_OR_NOTE_UPDATED",
            target = target.email,
            summary = "Updated custom daily limit to ${customLimit ?: "default"} and saved internal note.",
            succeeded = true
        )
    }

    suspend fun changeUserPlanDirectly(
        actingAdminId: String,
        targetUserId: String,
        grantPremium: Boolean,
        durationDays: Int = 30
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        val target = dao.getUserById(targetUserId) ?: error("Target user not found.")
        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity()
        val now = clockMillis()

        if (grantPremium) {
            dao.upsertSubscription(
                SubscriptionEntity(
                    id = "sub_${target.id}",
                    userId = target.id,
                    planType = "PREMIUM_MONTHLY",
                    status = "APPROVED",
                    dailyMinutesAllowance = settings.premiumOnlineDailyMinutesLimit,
                    startDate = now,
                    expirationDate = now + durationDays.toLong() * 86_400_000L,
                    renewalPolicy = "ADMIN_GRANT",
                    approvedByAdminId = admin.id,
                    approvalRequestId = "direct_${target.id}_$now",
                    updatedAt = now
                )
            )
        } else {
            val existing = dao.getSubscriptionForUser(target.id)
            if (existing != null) {
                dao.upsertSubscription(
                    existing.copy(
                        planType = "FREE_OFFLINE",
                        status = "SUSPENDED",
                        expirationDate = now,
                        updatedAt = now
                    )
                )
            }
        }
        recordAudit(
            admin = admin,
            actionType = "USER_PLAN_CHANGED",
            target = target.email,
            summary = if (grantPremium) "Granted Premium ($durationDays days)." else "Reverted user to Free Offline plan.",
            succeeded = true
        )
    }

    suspend fun resetUserDailyQuota(
        actingAdminId: String,
        targetUserId: String
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        val target = dao.getUserById(targetUserId) ?: error("Target user not found.")
        val utcDate = quotaRepo.currentUtcDateString()
        val id = "${target.id}_$utcDate"
        val existing = dao.getUsageRecord(id)
        if (existing != null) {
            dao.upsertUsageRecord(
                existing.copy(freeTurnsUsed = 0, premiumSecondsUsed = 0L, updatedAt = clockMillis())
            )
        } else {
            dao.upsertUsageRecord(UsageRecordEntity(id = id, userId = target.id, utcDate = utcDate))
        }
        recordAudit(admin, "USER_QUOTA_RESET", target.email, "Reset daily usage counters for $utcDate.", true)
    }

    suspend fun updateGlobalUsageControls(
        actingAdminId: String,
        updatedSettings: ApplicationSettingsEntity
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        require(updatedSettings.freeOfflineDailyTurnsLimit in 1..500) { "Free daily turns must be 1–500." }
        require(updatedSettings.premiumOnlineDailyMinutesLimit in 5..1440) { "Premium daily minutes must be 5–1440." }
        require(updatedSettings.maxConversationTurnsPerSession in 5..200) { "Max session turns must be 5–200." }
        require(updatedSettings.defaultSubscriptionDurationDays in 1..3650) { "Default subscription days must be 1–3650." }
        require(updatedSettings.rateLimitRequestsPerMinute in 2..120) { "Rate limit must be 2–120 req/min." }

        val existing = dao.getApplicationSettings() ?: ApplicationSettingsEntity()
        dao.upsertApplicationSettings(
            updatedSettings.copy(
                singletonId = "GLOBAL_CONFIG",
                adminProvisionTokenConsumed = existing.adminProvisionTokenConsumed,
                adminProvisionedUserId = existing.adminProvisionedUserId,
                adminProvisionedAt = existing.adminProvisionedAt,
                updatedAt = clockMillis()
            )
        )
        recordAudit(
            admin = admin,
            actionType = "GLOBAL_USAGE_CONTROLS_UPDATED",
            target = "GLOBAL_CONFIG",
            summary = "Updated limits: free=${updatedSettings.freeOfflineDailyTurnsLimit} turns, premium=${updatedSettings.premiumOnlineDailyMinutesLimit} mins, maintenance=${updatedSettings.maintenanceMode}.",
            succeeded = true
        )
    }

    suspend fun createOrUpdateSurvey(
        actingAdminId: String,
        title: String,
        description: String,
        mcQuestion: String,
        mcOptions: List<String>,
        freeTextQuestion: String,
        isPublished: Boolean
    ): Result<SurveyEntity> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        require(title.trim().length in 3..120) { "Survey title must be 3–120 characters." }
        val cleanOptions = mcOptions.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleanOptions.size >= 2) { "Provide at least 2 multiple-choice options." }

        val survey = SurveyEntity(
            id = "srv_${UUID.randomUUID().toString().take(10)}",
            title = title.trim(),
            description = description.trim(),
            mcQuestion = mcQuestion.trim(),
            mcOptionsPipe = cleanOptions.joinToString("|"),
            freeTextQuestion = freeTextQuestion.trim(),
            isPublished = isPublished,
            createdByAdminId = admin.id,
            createdAt = clockMillis()
        )
        dao.upsertSurvey(survey)
        recordAudit(admin, "SURVEY_CREATED", survey.id, "Created survey '${survey.title}' (published=$isPublished).", true)
        survey
    }

    suspend fun setSurveyPublished(
        actingAdminId: String,
        survey: SurveyEntity,
        published: Boolean
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        dao.upsertSurvey(survey.copy(isPublished = published))
        recordAudit(admin, "SURVEY_STATUS_CHANGED", survey.id, "Set survey published=$published.", true)
    }

    suspend fun updateSupportMessageStatus(
        actingAdminId: String,
        feedbackId: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        require(newStatus in setOf("OPEN", "REVIEWED", "RESOLVED")) { "Invalid support message status." }
        dao.updateFeedbackStatus(feedbackId, newStatus)
        recordAudit(admin, "SUPPORT_MESSAGE_STATUS_UPDATED", feedbackId, "Updated support message status to $newStatus.", true)
    }

    suspend fun exportMinimizedAdminReportJson(actingAdminId: String): Result<String> = runCatching {
        val admin = requireAdminUser(actingAdminId)
        val metrics = getOverviewMetrics(actingAdminId).getOrThrow()
        val users = dao.searchUsersPaginated("", "ALL", limit = 200, offset = 0)

        val json = JSONObject().apply {
            put("generatedAtMillis", clockMillis())
            put("generatedByAdmin", admin.email)
            put("privacyPolicy", "Minimized report: excludes password hashes, tokens, and private conversation transcripts.")
            put(
                "overview",
                JSONObject().apply {
                    put("totalRegisteredUsers", metrics.totalRegisteredUsers)
                    put("activePremiumSubscriptions", metrics.activePremiumSubscriptions)
                    put("pendingRequests", metrics.pendingSubscriptionRequests)
                    put("totalAiRequests", metrics.totalAiConversationRequests)
                }
            )
            put(
                "usersSummary",
                JSONArray().apply {
                    users.forEach { u ->
                        put(
                            JSONObject().apply {
                                put("userId", u.id)
                                put("accountStatus", u.accountStatus)
                                put("activeTargetLanguage", u.activeTargetLanguage)
                                put("proficiencyLevel", u.proficiencyLevel)
                            }
                        )
                    }
                }
            )
        }.toString(2)

        recordAudit(admin, "EXPORT_MINIMIZED_REPORT", "ADMIN_REPORT", "Exported minimized aggregate report (${users.size} user summaries).", true)
        json
    }
}
