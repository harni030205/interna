package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InternaDao {

    // --- Users & Roles ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :normalizedEmail AND accountStatus != 'DELETED' LIMIT 1")
    suspend fun getUserByEmail(normalizedEmail: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId AND accountStatus != 'DELETED' LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId AND accountStatus != 'DELETED' LIMIT 1")
    fun observeUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT COUNT(*) FROM users WHERE accountStatus != 'DELETED'")
    suspend fun countTotalUsers(): Int

    @Query("SELECT COUNT(*) FROM users WHERE createdAt >= :sinceMillis AND accountStatus != 'DELETED'")
    suspend fun countNewUsersSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM users WHERE lastActiveAt >= :sinceMillis AND accountStatus != 'DELETED'")
    suspend fun countActiveUsersSince(sinceMillis: Long): Int

    @Query(
        """
        SELECT * FROM users
        WHERE accountStatus != 'DELETED'
        AND (:searchQuery = '' OR LOWER(fullName) LIKE '%' || LOWER(:searchQuery) || '%' OR LOWER(email) LIKE '%' || LOWER(:searchQuery) || '%' OR LOWER(id) LIKE '%' || LOWER(:searchQuery) || '%')
        AND (:statusFilter = 'ALL' OR accountStatus = :statusFilter)
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchUsersPaginated(
        searchQuery: String,
        statusFilter: String,
        limit: Int,
        offset: Int
    ): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRolePermission(rolePermission: RolePermissionEntity)

    @Query("SELECT * FROM roles_permissions WHERE userId = :userId LIMIT 1")
    suspend fun getRolePermission(userId: String): RolePermissionEntity?

    @Query("SELECT * FROM roles_permissions WHERE userId = :userId LIMIT 1")
    fun observeRolePermission(userId: String): Flow<RolePermissionEntity?>

    // --- Preferences ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(preferences: UserPreferenceEntity)

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    fun observePreferences(userId: String): Flow<UserPreferenceEntity?>

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    suspend fun getPreferences(userId: String): UserPreferenceEntity?

    // --- Subscriptions & Requests ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE userId = :userId LIMIT 1")
    suspend fun getSubscriptionForUser(userId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE userId = :userId LIMIT 1")
    fun observeSubscriptionForUser(userId: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE approvalRequestId = :requestId LIMIT 1")
    suspend fun getSubscriptionByApprovalRequestId(requestId: String): SubscriptionEntity?

    @Query("SELECT COUNT(*) FROM subscriptions WHERE status = 'APPROVED' AND expirationDate > :nowMillis")
    suspend fun countActivePremiumSubscriptions(nowMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubscriptionRequest(request: SubscriptionRequestEntity)

    @Update
    suspend fun updateSubscriptionRequest(request: SubscriptionRequestEntity)

    @Query("SELECT * FROM subscription_requests WHERE id = :requestId LIMIT 1")
    suspend fun getSubscriptionRequestById(requestId: String): SubscriptionRequestEntity?

    @Query("SELECT * FROM subscription_requests WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserSubscriptionRequests(userId: String): Flow<List<SubscriptionRequestEntity>>

    @Query("SELECT * FROM subscription_requests WHERE userId = :userId AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingRequestForUser(userId: String): SubscriptionRequestEntity?

    @Query("SELECT * FROM subscription_requests ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, createdAt DESC")
    fun observeAllSubscriptionRequests(): Flow<List<SubscriptionRequestEntity>>

    @Query("SELECT COUNT(*) FROM subscription_requests WHERE status = :status")
    suspend fun countRequestsByStatus(status: String): Int

    @Query("SELECT * FROM subscription_requests WHERE normalizedTxRef = :normalizedTxRef AND normalizedTxRef != '' LIMIT 1")
    suspend fun findAnyRequestByNormalizedTxRef(normalizedTxRef: String): SubscriptionRequestEntity?

    // --- Mobile-Money Payment Method Configurations ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultPaymentMethodConfigs(configs: List<PaymentMethodConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentMethodConfig(config: PaymentMethodConfigEntity)

    @Query("SELECT * FROM payment_method_configs WHERE methodCode = :methodCode LIMIT 1")
    suspend fun getPaymentMethodConfig(methodCode: String): PaymentMethodConfigEntity?

    @Query("SELECT * FROM payment_method_configs ORDER BY methodCode ASC")
    suspend fun getAllPaymentMethodConfigs(): List<PaymentMethodConfigEntity>

    @Query("SELECT * FROM payment_method_configs ORDER BY CASE methodCode WHEN 'YAS_MONEY' THEN 0 WHEN 'AIRTEL_MONEY' THEN 1 WHEN 'ORANGE_MONEY' THEN 2 ELSE 3 END ASC")
    fun observePaymentMethodConfigs(): Flow<List<PaymentMethodConfigEntity>>

    @Transaction
    suspend fun approveSubscriptionRequestAtomically(
        requestId: String,
        adminId: String,
        durationDays: Int,
        dailyMinutesAllowance: Int,
        renewalPolicy: String,
        nowMillis: Long
    ): Boolean {
        val existingRequest = getSubscriptionRequestById(requestId) ?: return false
        if (existingRequest.status != "PENDING") {
            // Prevents duplicate or replayed approvals
            return false
        }
        val alreadyLinked = getSubscriptionByApprovalRequestId(requestId)
        if (alreadyLinked != null) {
            return false
        }

        val updatedReq = existingRequest.copy(
            status = "APPROVED",
            durationDays = durationDays,
            reviewedByAdminId = adminId,
            reviewedAt = nowMillis
        )
        updateSubscriptionRequest(updatedReq)

        val approvedPlanCode = existingRequest.requestedPlan.ifBlank { "PREMIUM_MONTHLY" }
        val expirationMillis = nowMillis + durationDays.toLong() * 24L * 60L * 60L * 1000L
        val currentSub = getSubscriptionForUser(existingRequest.userId)
        val newSub = SubscriptionEntity(
            id = currentSub?.id ?: "sub_${existingRequest.userId}",
            userId = existingRequest.userId,
            planType = approvedPlanCode,
            status = "APPROVED",
            dailyMinutesAllowance = dailyMinutesAllowance,
            startDate = nowMillis,
            expirationDate = expirationMillis,
            renewalPolicy = renewalPolicy,
            approvedByAdminId = adminId,
            approvalRequestId = requestId,
            updatedAt = nowMillis
        )
        upsertSubscription(newSub)

        insertNotification(
            NotificationEntity(
                id = "notif_approve_$requestId",
                userId = existingRequest.userId,
                title = "Payment Verified — Premium Active",
                body = "Your $approvedPlanCode payment (${existingRequest.transactionReference.ifBlank { requestId }}) was verified and approved for $durationDays days ($dailyMinutesAllowance mins/day).",
                type = "SUBSCRIPTION",
                createdAt = nowMillis
            )
        )
        return true
    }

    @Transaction
    suspend fun consumeProvisionTokenAndGrantAdminAtomically(
        userToCreateOrUpdate: UserEntity,
        isNewAccount: Boolean,
        roleGrant: RolePermissionEntity,
        auditLog: AdminAuditLogEntity,
        nowMillis: Long
    ): Boolean {
        val settings = getApplicationSettings() ?: ApplicationSettingsEntity(updatedAt = nowMillis)
        if (settings.adminProvisionTokenConsumed) {
            return false
        }
        if (isNewAccount) {
            insertUser(userToCreateOrUpdate)
            upsertPreferences(
                UserPreferenceEntity(
                    userId = userToCreateOrUpdate.id,
                    interfaceLanguage = "en",
                    themeMode = "DARK",
                    updatedAt = nowMillis
                )
            )
        } else {
            updateUser(userToCreateOrUpdate)
        }
        upsertRolePermission(roleGrant)
        upsertApplicationSettings(
            settings.copy(
                adminProvisionTokenConsumed = true,
                adminProvisionedUserId = userToCreateOrUpdate.id,
                adminProvisionedAt = nowMillis,
                updatedAt = nowMillis
            )
        )
        insertAuditLog(auditLog)
        return true
    }

    // --- Usage Metering ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsageRecord(record: UsageRecordEntity)

    @Query("SELECT * FROM usage_records WHERE id = :recordId LIMIT 1")
    suspend fun getUsageRecord(recordId: String): UsageRecordEntity?

    @Query("SELECT * FROM usage_records WHERE userId = :userId AND utcDate = :utcDate LIMIT 1")
    fun observeUsageRecord(userId: String, utcDate: String): Flow<UsageRecordEntity?>

    @Query("SELECT COALESCE(SUM(freeTurnsUsed), 0) FROM usage_records WHERE userId = :userId")
    suspend fun sumUserTotalTurns(userId: String): Int

    @Query("SELECT COALESCE(SUM(premiumSecondsUsed), 0) FROM usage_records WHERE userId = :userId")
    suspend fun sumUserTotalPremiumSeconds(userId: String): Long

    @Query("SELECT COALESCE(SUM(aiRequestsCount), 0) FROM usage_records")
    suspend fun sumGlobalAiRequests(): Int

    @Query("SELECT COALESCE(SUM(apiErrorCount), 0) FROM usage_records")
    suspend fun sumGlobalApiErrors(): Int

    @Query("SELECT COALESCE(SUM(estimatedTokensUsed), 0) FROM usage_records")
    suspend fun sumGlobalEstimatedTokens(): Long

    @Query("SELECT COUNT(*) FROM usage_records WHERE utcDate = :utcDate AND (freeTurnsUsed >= :warnTurnsThreshold OR premiumSecondsUsed >= :warnSecondsThreshold)")
    suspend fun countUsageLimitWarningsToday(utcDate: String, warnTurnsThreshold: Int, warnSecondsThreshold: Long): Int

    // --- Conversations (Isolated per User; Admins never query message contents by default) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversationSession(session: ConversationSessionEntity)

    @Query("SELECT * FROM conversation_sessions WHERE id = :sessionId AND userId = :userId LIMIT 1")
    suspend fun getSessionForUser(sessionId: String, userId: String): ConversationSessionEntity?

    @Query("SELECT * FROM conversation_sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserSessions(userId: String): Flow<List<ConversationSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversationMessage(message: ConversationMessageEntity)

    @Query("SELECT * FROM conversation_messages WHERE sessionId = :sessionId AND userId = :userId ORDER BY createdAt ASC")
    fun observeMessagesForSession(sessionId: String, userId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE sessionId = :sessionId AND userId = :userId ORDER BY createdAt ASC")
    suspend fun getMessagesForSession(sessionId: String, userId: String): List<ConversationMessageEntity>

    // --- Learning Progress & Offline Sync Queue ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearningProgress(progress: LearningProgressEntity)

    @Query("SELECT * FROM learning_progress WHERE userId = :userId ORDER BY completedAt DESC")
    fun observeUserProgress(userId: String): Flow<List<LearningProgressEntity>>

    @Query("SELECT * FROM learning_progress WHERE userId = :userId ORDER BY completedAt DESC")
    suspend fun getUserProgressList(userId: String): List<LearningProgressEntity>

    @Query("SELECT * FROM learning_progress WHERE userId = :userId AND syncedToServer = 0")
    suspend fun getUnsyncedProgress(userId: String): List<LearningProgressEntity>

    @Query("UPDATE learning_progress SET syncedToServer = 1 WHERE userId = :userId AND syncedToServer = 0")
    suspend fun markAllProgressSynced(userId: String)

    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM learning_progress WHERE userId = :userId")
    suspend fun sumUserTotalXp(userId: String): Int

    // --- Saved Vocabulary & Flashcards ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedVocabulary(item: SavedVocabularyEntity)

    @Query("SELECT * FROM saved_vocabulary WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeSavedVocabulary(userId: String): Flow<List<SavedVocabularyEntity>>

    @Query("SELECT * FROM saved_vocabulary WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getSavedVocabularyList(userId: String): List<SavedVocabularyEntity>

    @Query("DELETE FROM saved_vocabulary WHERE id = :vocabId AND userId = :userId")
    suspend fun deleteVocabularyItem(vocabId: String, userId: String)

    // --- Dictionary Cache ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDictionaryCache(entry: DictionaryCacheEntity)

    @Query("SELECT * FROM dictionary_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getDictionaryCache(cacheKey: String): DictionaryCacheEntity?

    @Query("SELECT * FROM dictionary_cache ORDER BY cachedAt DESC LIMIT 50")
    fun observeRecentDictionaryCache(): Flow<List<DictionaryCacheEntity>>

    // --- Offline Lessons ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineLessons(lessons: List<OfflineLessonEntity>)

    @Query("SELECT COUNT(*) FROM offline_lessons")
    suspend fun countOfflineLessons(): Int

    @Query("SELECT * FROM offline_lessons WHERE languageCode = :languageCode ORDER BY id ASC")
    fun observeLessonsForLanguage(languageCode: String): Flow<List<OfflineLessonEntity>>

    @Query("SELECT * FROM offline_lessons ORDER BY languageCode ASC, id ASC")
    fun observeAllOfflineLessons(): Flow<List<OfflineLessonEntity>>

    @Query("UPDATE offline_lessons SET isDownloadedOffline = :downloaded WHERE id = :lessonId")
    suspend fun setLessonDownloaded(lessonId: String, downloaded: Boolean)

    // --- Surveys & Feedback ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSurvey(survey: SurveyEntity)

    @Query("SELECT * FROM surveys ORDER BY createdAt DESC")
    fun observeAllSurveys(): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE isPublished = 1 ORDER BY createdAt DESC")
    fun observePublishedSurveys(): Flow<List<SurveyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSurveyResponse(response: SurveyResponseEntity)

    @Query("SELECT * FROM survey_responses WHERE surveyId = :surveyId")
    suspend fun getResponsesForSurvey(surveyId: String): List<SurveyResponseEntity>

    @Query("SELECT * FROM survey_responses")
    fun observeAllSurveyResponses(): Flow<List<SurveyResponseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackEntity)

    @Query("SELECT * FROM feedback ORDER BY createdAt DESC")
    fun observeAllFeedback(): Flow<List<FeedbackEntity>>

    @Query("SELECT * FROM feedback WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserFeedback(userId: String): Flow<List<FeedbackEntity>>

    @Query("UPDATE feedback SET status = :status WHERE id = :feedbackId")
    suspend fun updateFeedbackStatus(feedbackId: String, status: String)

    // --- Notifications ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllNotificationsRead(userId: String)

    // --- Administrative Audit Logs ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: AdminAuditLogEntity)

    @Query("SELECT * FROM administrative_audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeAuditLogs(): Flow<List<AdminAuditLogEntity>>

    @Query("SELECT * FROM administrative_audit_logs ORDER BY timestamp DESC LIMIT 200")
    suspend fun getAuditLogsList(): List<AdminAuditLogEntity>

    // --- Application Settings ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApplicationSettings(settings: ApplicationSettingsEntity)

    @Query("SELECT * FROM application_settings WHERE singletonId = 'GLOBAL_CONFIG' LIMIT 1")
    suspend fun getApplicationSettings(): ApplicationSettingsEntity?

    @Query("SELECT * FROM application_settings WHERE singletonId = 'GLOBAL_CONFIG' LIMIT 1")
    fun observeApplicationSettings(): Flow<ApplicationSettingsEntity?>

    // --- Complete Personal Data Deletion (GDPR / Account Deletion) ---
    @Query("DELETE FROM conversation_messages WHERE userId = :userId")
    suspend fun deleteUserMessages(userId: String)

    @Query("DELETE FROM conversation_sessions WHERE userId = :userId")
    suspend fun deleteUserSessions(userId: String)

    @Query("DELETE FROM saved_vocabulary WHERE userId = :userId")
    suspend fun deleteUserVocabulary(userId: String)

    @Query("DELETE FROM learning_progress WHERE userId = :userId")
    suspend fun deleteUserProgress(userId: String)

    @Query("DELETE FROM usage_records WHERE userId = :userId")
    suspend fun deleteUserUsage(userId: String)

    @Query("DELETE FROM subscriptions WHERE userId = :userId")
    suspend fun deleteUserSubscriptions(userId: String)

    @Query("DELETE FROM subscription_requests WHERE userId = :userId")
    suspend fun deleteUserSubscriptionRequests(userId: String)

    @Query("DELETE FROM user_preferences WHERE userId = :userId")
    suspend fun deleteUserPreferences(userId: String)

    @Query("DELETE FROM roles_permissions WHERE userId = :userId")
    suspend fun deleteUserRoles(userId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteUserNotifications(userId: String)

    @Query("DELETE FROM survey_responses WHERE userId = :userId")
    suspend fun deleteUserSurveyResponses(userId: String)

    @Query("DELETE FROM feedback WHERE userId = :userId")
    suspend fun deleteUserFeedback(userId: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun hardDeleteUserRow(userId: String)

    @Transaction
    suspend fun purgeAllUserData(userId: String) {
        deleteUserMessages(userId)
        deleteUserSessions(userId)
        deleteUserVocabulary(userId)
        deleteUserProgress(userId)
        deleteUserUsage(userId)
        deleteUserSubscriptions(userId)
        deleteUserSubscriptionRequests(userId)
        deleteUserPreferences(userId)
        deleteUserRoles(userId)
        deleteUserNotifications(userId)
        deleteUserSurveyResponses(userId)
        deleteUserFeedback(userId)
        hardDeleteUserRow(userId)
    }
}
