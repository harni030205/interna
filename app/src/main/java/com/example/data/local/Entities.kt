package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String, // Normalized lowercase
    val passwordHash: String,
    val passwordSalt: String,
    val emailVerified: Boolean = false,
    val verificationCodeHash: String = "",
    val resetTokenHash: String = "",
    val resetTokenExpiresAt: Long = 0L,
    val nativeLanguage: String = "en",
    val targetLanguagesCsv: String = "fr,es,ja",
    val activeTargetLanguage: String = "fr",
    val proficiencyLevel: String = "Intermediate",
    val learningGoal: String = "Conversational Fluency",
    val dailyStudyMinutesGoal: Int = 20,
    val accountStatus: String = "ACTIVE", // ACTIVE, SUSPENDED, DELETED
    val customDailyAllowanceOverride: Int? = null,
    val adminInternalNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "roles_permissions")
data class RolePermissionEntity(
    @PrimaryKey val userId: String,
    val role: String, // USER or ADMIN
    val grantedBy: String,
    val grantSignature: String, // HMAC-SHA256 verified on every admin check
    val grantedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val userId: String,
    val interfaceLanguage: String = "en",
    val themeMode: String = "DARK", // DARK, LIGHT, SYSTEM
    val saveConversationHistory: Boolean = true,
    val nativeExplanationsEnabled: Boolean = true,
    val preferredAccent: String = "fr-FR",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subscriptions",
    indices = [
        Index(value = ["userId"], unique = true),
        Index(value = ["approvalRequestId"], unique = true)
    ]
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val planType: String = "FREE_OFFLINE", // FREE_OFFLINE, PREMIUM_ONLINE
    val status: String = "NONE", // NONE, PENDING, APPROVED, REJECTED, EXPIRED, SUSPENDED
    val dailyMinutesAllowance: Int = 120,
    val startDate: Long = 0L,
    val expirationDate: Long = 0L,
    val renewalPolicy: String = "MANUAL_RENEWAL",
    val approvedByAdminId: String = "",
    val approvalRequestId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subscription_requests",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["status"]),
        Index(value = ["normalizedTxRef"])
    ]
)
data class SubscriptionRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userEmail: String,
    val userName: String,
    val requestedPlan: String = "PREMIUM_MONTHLY",
    val optionalMessage: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, EXPIRED, SUSPENDED
    val rejectionReason: String = "",
    val durationDays: Int = 30,
    val reviewedByAdminId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long = 0L,
    @ColumnInfo(defaultValue = "''") val paymentMethod: String = "", // YAS_MONEY, AIRTEL_MONEY, ORANGE_MONEY
    @ColumnInfo(defaultValue = "''") val transactionReference: String = "",
    @ColumnInfo(defaultValue = "''") val normalizedTxRef: String = "",
    @ColumnInfo(defaultValue = "''") val amountPaid: String = "",
    @ColumnInfo(defaultValue = "''") val currency: String = "",
    @ColumnInfo(defaultValue = "''") val paymentDate: String = "", // YYYY-MM-DD
    @ColumnInfo(defaultValue = "''") val proofOfPaymentUri: String = "",
    @ColumnInfo(defaultValue = "'MANUAL_VERIFICATION'") val verificationMode: String = "MANUAL_VERIFICATION"
)

@Entity(tableName = "payment_method_configs")
data class PaymentMethodConfigEntity(
    @PrimaryKey val methodCode: String, // YAS_MONEY, AIRTEL_MONEY, ORANGE_MONEY
    val displayName: String,
    val isEnabled: Boolean = true,
    val receivingAccountNumber: String = "",
    val receivingAccountName: String = "",
    val currency: String = "MGA",
    val monthlyPrice: String = "",
    val quarterlyPrice: String = "",
    val yearlyPrice: String = "",
    val transferInstructions: String = "",
    val updatedByAdminId: String = "",
    val updatedAt: Long = 0L
)

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["userId", "utcDate"], unique = true)]
)
data class UsageRecordEntity(
    @PrimaryKey val id: String, // "${userId}_${utcDate}"
    val userId: String,
    val utcDate: String, // YYYY-MM-DD in UTC timezone policy
    val freeTurnsUsed: Int = 0,
    val premiumSecondsUsed: Long = 0L,
    val aiRequestsCount: Int = 0,
    val apiErrorCount: Int = 0,
    val estimatedTokensUsed: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversation_sessions",
    indices = [Index(value = ["userId"])]
)
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val languageCode: String,
    val accentTag: String,
    val scenarioId: String,
    val difficultyLevel: String,
    val turnsCount: Int = 0,
    val activeDurationSeconds: Long = 0L,
    val summaryText: String = "",
    val correctionsSummary: String = "",
    val newVocabularySummary: String = "",
    val sessionScore: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

@Entity(
    tableName = "conversation_messages",
    indices = [Index(value = ["sessionId"]), Index(value = ["userId"])]
)
data class ConversationMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val userId: String,
    val sender: String, // "USER" or "AI"
    val content: String,
    val grammarCorrection: String = "",
    val vocabularySuggestions: String = "",
    val naturalAlternative: String = "",
    val nativeExplanation: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learning_progress",
    indices = [Index(value = ["userId"]), Index(value = ["languageCode"])]
)
data class LearningProgressEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val languageCode: String,
    val skillCategory: String, // VOCABULARY, GRAMMAR, LISTENING, READING, SPEAKING, WRITING, QUIZ
    val lessonId: String,
    val lessonTitle: String,
    val scorePercent: Int,
    val xpEarned: Int,
    val studyMinutes: Int,
    val utcDate: String,
    val syncedToServer: Boolean = false,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_vocabulary",
    indices = [Index(value = ["userId"]), Index(value = ["userId", "word", "targetLanguage"], unique = true)]
)
data class SavedVocabularyEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val word: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val translation: String,
    val partOfSpeech: String,
    val ipaPhonetic: String?, // Nullable — never invented
    val audioUrl: String?, // Nullable — never faked
    val definition: String,
    val exampleSentence: String,
    val masteryLevel: Int = 0, // 0..5 for flashcards
    val isDifficult: Boolean = false,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary_cache")
data class DictionaryCacheEntity(
    @PrimaryKey val cacheKey: String, // "${sourceLang}_${targetLang}_${word.lowercase()}"
    val word: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val hasVerifiedEntry: Boolean,
    val verifiedPartOfSpeech: String,
    val verifiedDefinitionsPipe: String,
    val verifiedIpa: String?,
    val verifiedAudioUrl: String?,
    val verifiedExamplesPipe: String,
    val verifiedSynonymsPipe: String,
    val verifiedAntonymsPipe: String,
    val hasAiExplanation: Boolean,
    val aiTranslation: String,
    val aiContextualMeaning: String,
    val aiCollocationsPipe: String,
    val aiFormalVsInformal: String,
    val aiIdiomsPipe: String,
    val aiVerbConjugationsPipe: String,
    val aiNounGenderAndPlural: String,
    val aiEtymologyNote: String,
    val aiRegionalVariants: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "offline_lessons",
    indices = [Index(value = ["languageCode"])]
)
data class OfflineLessonEntity(
    @PrimaryKey val id: String,
    val languageCode: String,
    val level: String,
    val skillCategory: String, // VOCABULARY, GRAMMAR, LISTENING, READING, SPEAKING, WRITING, QUIZ
    val title: String,
    val summary: String,
    val lessonBody: String,
    val targetPhraseForAudio: String,
    val quizQuestion: String,
    val quizOptionsPipe: String, // 4 pipe-separated options
    val quizCorrectIndex: Int,
    val quizExplanation: String,
    val estimatedMinutes: Int = 8,
    val isDownloadedOffline: Boolean = true
)

@Entity(tableName = "surveys")
data class SurveyEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val mcQuestion: String,
    val mcOptionsPipe: String,
    val freeTextQuestion: String,
    val isPublished: Boolean = true,
    val createdByAdminId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "survey_responses",
    indices = [Index(value = ["surveyId", "userId"], unique = true)]
)
data class SurveyResponseEntity(
    @PrimaryKey val id: String,
    val surveyId: String,
    val userId: String,
    val selectedMcOption: String,
    val freeTextAnswer: String,
    val submittedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "feedback",
    indices = [Index(value = ["userId"]), Index(value = ["category"])]
)
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userEmail: String,
    val category: String, // BUG_REPORT, FEATURE_SUGGESTION, CONTENT_ISSUE, GENERAL
    val subject: String,
    val message: String,
    val status: String = "OPEN", // OPEN, REVIEWED, RESOLVED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["userId"])]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val type: String, // SUBSCRIPTION, QUOTA, SYSTEM
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "administrative_audit_logs",
    indices = [Index(value = ["timestamp"])]
)
data class AdminAuditLogEntity(
    @PrimaryKey val id: String,
    val adminId: String,
    val adminEmail: String,
    val actionType: String,
    val affectedTarget: String,
    val sanitizedSummary: String,
    val succeeded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "application_settings")
data class ApplicationSettingsEntity(
    @PrimaryKey val singletonId: String = "GLOBAL_CONFIG",
    val freeOfflineDailyTurnsLimit: Int = 20,
    val premiumOnlineDailyMinutesLimit: Int = 120, // 2 hours
    val supportedLanguagesCsv: String = "en,fr,es,de,ja,zh,ar,it,pt,ko,ru,mg",
    val maxConversationTurnsPerSession: Int = 50,
    val aiConversationEnabled: Boolean = true,
    val dictionaryAiEnabled: Boolean = true,
    val pronunciationCoachEnabled: Boolean = true,
    val defaultSubscriptionDurationDays: Int = 30,
    val maintenanceMode: Boolean = false,
    val accountSuspensionErrorThreshold: Int = 25,
    val rateLimitRequestsPerMinute: Int = 15,
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val adminProvisionTokenConsumed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val adminProvisionedUserId: String = "",
    @ColumnInfo(defaultValue = "0") val adminProvisionedAt: Long = 0L
)
