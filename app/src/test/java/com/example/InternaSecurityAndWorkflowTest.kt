package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApplicationSettingsEntity
import com.example.data.local.FeedbackEntity
import com.example.data.local.InternaDao
import com.example.data.local.InternaDatabase
import com.example.data.remote.BackendSecurityGatewayClient
import com.example.data.remote.DictionaryApiClient
import com.example.data.remote.GeminiApiClient
import com.example.data.repository.AdminConsoleRepository
import com.example.data.repository.AuthAndUserRepository
import com.example.data.repository.LanguageLearningRepository
import com.example.data.repository.QuotaAndSubscriptionRepository
import com.example.domain.model.MobileMoneyMethod
import com.example.domain.model.PremiumPlanOption
import com.example.security.CryptoSecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InternaSecurityAndWorkflowTest {

    private lateinit var db: InternaDatabase
    private lateinit var dao: InternaDao
    private lateinit var crypto: CryptoSecurityManager
    private lateinit var gateway: BackendSecurityGatewayClient
    private var simulatedNowMillis: Long = 1_761_696_000_000L // 2025-10-29 00:00:00 UTC

    private val testAdminSecret = "super-secret-provision-token-2026-x9k2"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InternaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
        crypto = CryptoSecurityManager(configuredAdminProvisionToken = testAdminSecret)
        gateway = BackendSecurityGatewayClient(backendUrlProvider = { "DIRECT_GEMINI_MODE" })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `authentication hashes password and never grants admin merely by email`() = runBlocking {
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        authRepo.ensureSeedDataInitialized()

        val regResult = authRepo.registerUser(
            fullName = "Harinirina Admin Candidate",
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "StrongPassword#2026",
            nativeLanguage = "en",
            primaryTargetLanguage = "fr",
            proficiencyLevel = "Advanced",
            learningGoal = "Professional Fluency"
        )
        assertTrue(regResult.isSuccess)
        val outcome = regResult.getOrThrow()

        val storedUser = dao.getUserById(outcome.user.id)!!
        assertNotEquals("StrongPassword#2026", storedUser.passwordHash)
        assertTrue(storedUser.passwordSalt.isNotBlank())

        assertFalse(authRepo.isVerifiedAdministrator(storedUser.id))
        assertTrue(authRepo.signInDeveloper(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, "StrongPassword#2026").isFailure)

        assertTrue(authRepo.signIn(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, "StrongPassword#2026").isSuccess)
        assertTrue(authRepo.signIn(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, "WrongPassword!").isFailure)

        val resetChallenge = authRepo.requestPasswordReset(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL).getOrThrow()
        assertTrue(
            authRepo.confirmPasswordReset(
                email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
                code = resetChallenge.resetCodeDispatchPreview,
                newPassword = "UpdatedPassword#2027"
            ).isSuccess
        )
        assertTrue(authRepo.signIn(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, "UpdatedPassword#2027").isSuccess)
    }

    @Test
    fun `dedicated developer login provisions harinirinaianjara directly without prior registration and blocks token reuse`() = runBlocking {
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        val quotaRepo = QuotaAndSubscriptionRepository(dao) { simulatedNowMillis }
        val adminRepo = AdminConsoleRepository(dao, authRepo, quotaRepo, gateway) { simulatedNowMillis }

        // Wrong token fails and does not consume one-time lock
        assertTrue(
            authRepo.bootstrapOrUpgradeDeveloperAccount(
                email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
                password = "AdminSecurePassword#2026",
                fullName = "Harinirina Ianjara",
                suppliedProvisionToken = "wrong-provision-token-12345"
            ).isFailure
        )
        assertFalse(dao.getApplicationSettings()!!.adminProvisionTokenConsumed)

        // Direct one-time provisioning without prior normal-user registration succeeds
        val provisioned = authRepo.bootstrapOrUpgradeDeveloperAccount(
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "AdminSecurePassword#2026",
            fullName = "Harinirina Ianjara",
            suppliedProvisionToken = testAdminSecret
        )
        assertTrue(provisioned.isSuccess)
        val adminUser = provisioned.getOrThrow()
        assertEquals(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, adminUser.email)
        assertTrue(authRepo.isVerifiedAdministrator(adminUser.id))
        assertTrue(dao.getApplicationSettings()!!.adminProvisionTokenConsumed)

        // Developer can sign in via Dedicated Developer Login and access Developer Console
        val devSignIn = authRepo.signInDeveloper(
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "AdminSecurePassword#2026"
        )
        assertTrue(devSignIn.isSuccess)
        assertEquals(adminUser.id, devSignIn.getOrThrow().id)

        val metrics = adminRepo.getOverviewMetrics(adminUser.id)
        assertTrue(metrics.isSuccess)
        assertEquals(1, metrics.getOrThrow().totalRegisteredUsers)

        // Reusing the one-time provisioning token is strictly blocked
        val reuseAttempt = authRepo.bootstrapOrUpgradeDeveloperAccount(
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "AdminSecurePassword#2026",
            fullName = "Harinirina Ianjara",
            suppliedProvisionToken = testAdminSecret
        )
        assertTrue(reuseAttempt.isFailure)
        val logs = dao.observeAuditLogs().first()
        assertTrue(logs.any { it.actionType == "ADMIN_PROVISION_TOKEN_REUSE_BLOCKED" && !it.succeeded })
    }

    @Test
    fun `existing harinirinaianjara account upgrades in place to administrator without creating duplicate account`() = runBlocking {
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        authRepo.ensureSeedDataInitialized()

        // Suppose harinirinaianjara@gmail.com registered earlier as an ordinary user
        val existingReg = authRepo.registerUser(
            fullName = "Harinirina Existing",
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "ExistingDevPass#2026",
            nativeLanguage = "fr",
            primaryTargetLanguage = "en",
            proficiencyLevel = "Advanced",
            learningGoal = "Global Communication"
        ).getOrThrow()
        assertEquals(1, dao.countTotalUsers())
        assertFalse(authRepo.isVerifiedAdministrator(existingReg.user.id))

        // Wrong password cannot hijack upgrade even with valid token
        assertTrue(
            authRepo.bootstrapOrUpgradeDeveloperAccount(
                email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
                password = "WrongExistingPassword#999",
                suppliedProvisionToken = testAdminSecret
            ).isFailure
        )
        assertFalse(dao.getApplicationSettings()!!.adminProvisionTokenConsumed)

        // Correct password + one-time token upgrades the existing account in-place with zero duplicates
        val upgraded = authRepo.bootstrapOrUpgradeDeveloperAccount(
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "ExistingDevPass#2026",
            suppliedProvisionToken = testAdminSecret
        ).getOrThrow()

        assertEquals(1, dao.countTotalUsers())
        assertEquals(existingReg.user.id, upgraded.id)
        assertTrue(authRepo.isVerifiedAdministrator(upgraded.id))
        assertTrue(authRepo.signInDeveloper(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, "ExistingDevPass#2026").isSuccess)
    }

    @Test
    fun `normal user cannot sign in via developer login or invoke any administrator functions`() = runBlocking {
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        val quotaRepo = QuotaAndSubscriptionRepository(dao) { simulatedNowMillis }
        val adminRepo = AdminConsoleRepository(dao, authRepo, quotaRepo, gateway) { simulatedNowMillis }
        authRepo.ensureSeedDataInitialized()

        val ordinaryUser = authRepo.registerUser(
            "Alice Learner", "alice@example.com", "AlicePass#123", "en", "es", "Beginner", "Travel"
        ).getOrThrow().user

        // Ordinary user cannot use Developer Login or bootstrap admin even if knowing token
        assertTrue(authRepo.signInDeveloper("alice@example.com", "AlicePass#123").isFailure)
        assertTrue(
            authRepo.bootstrapOrUpgradeDeveloperAccount(
                email = "alice@example.com",
                password = "AlicePass#123",
                suppliedProvisionToken = testAdminSecret
            ).isFailure
        )

        // Ordinary user blocked from every Developer Console capability
        assertTrue(adminRepo.getOverviewMetrics(ordinaryUser.id).isFailure)
        assertTrue(
            adminRepo.updatePaymentMethodConfig(
                actingAdminId = ordinaryUser.id,
                methodCode = MobileMoneyMethod.YAS_MONEY.code,
                isEnabled = true,
                receivingAccountNumber = "0340000000",
                receivingAccountName = "Attacker",
                currency = "MGA",
                monthlyPrice = "1000",
                quarterlyPrice = "2500",
                yearlyPrice = "9000",
                transferInstructions = "Fake"
            ).isFailure
        )
        dao.insertFeedback(
            FeedbackEntity(
                id = "fb_1",
                userId = ordinaryUser.id,
                userEmail = ordinaryUser.email,
                category = "SUPPORT",
                subject = "Question",
                message = "Help please"
            )
        )
        assertTrue(adminRepo.updateSupportMessageStatus(ordinaryUser.id, "fb_1", "RESOLVED").isFailure)

        val logsAfterBlocked = dao.observeAuditLogs().first()
        assertTrue(logsAfterBlocked.any { it.actionType == "UNAUTHORIZED_ADMIN_ACCESS_BLOCKED" && !it.succeeded })
    }

    @Test
    fun `mobile money workflow supports Yas Airtel and Orange Money keeps pending until approved unlocks exact plan duration and blocks fraud`() = runBlocking {
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        val quotaRepo = QuotaAndSubscriptionRepository(dao) { simulatedNowMillis }
        val adminRepo = AdminConsoleRepository(dao, authRepo, quotaRepo, gateway) { simulatedNowMillis }
        authRepo.ensureSeedDataInitialized()

        val admin = authRepo.bootstrapOrUpgradeDeveloperAccount(
            email = CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
            password = "AdminSecurePassword#2026",
            suppliedProvisionToken = testAdminSecret
        ).getOrThrow()

        val learnerYas = authRepo.registerUser("Learner Yas", "yas@example.com", "LearnerPass#1", "mg", "en", "Beginner", "Career").getOrThrow().user
        val learnerAirtel = authRepo.registerUser("Learner Airtel", "airtel@example.com", "LearnerPass#2", "mg", "fr", "Intermediate", "Study").getOrThrow().user
        val learnerOrange = authRepo.registerUser("Learner Orange", "orange@example.com", "LearnerPass#3", "fr", "ja", "Beginner", "Travel").getOrThrow().user

        val paymentDateToday = quotaRepo.currentUtcDateString(simulatedNowMillis)

        // 1. Default configs have NO invented receiving numbers; submitting before admin configures fails
        val defaultConfigs = dao.observePaymentMethodConfigs().first()
        assertEquals(3, defaultConfigs.size)
        assertTrue(defaultConfigs.all { it.receivingAccountNumber.isBlank() && it.monthlyPrice.isBlank() })
        assertTrue(
            quotaRepo.submitMobileMoneyPaymentRequest(
                userId = learnerYas.id,
                requestedPlan = PremiumPlanOption.MONTHLY.planCode,
                paymentMethod = MobileMoneyMethod.YAS_MONEY.code,
                transactionReference = "YAS-UNCONFIG-01",
                amountPaid = "25000",
                paymentDate = paymentDateToday
            ).isFailure
        )

        // 2. Administrator configures all three payment methods in Developer Console
        assertTrue(
            adminRepo.updatePaymentMethodConfig(
                actingAdminId = admin.id,
                methodCode = MobileMoneyMethod.YAS_MONEY.code,
                isEnabled = true,
                receivingAccountNumber = "+261 34 11 222 33",
                receivingAccountName = "Interna Education Yas",
                currency = "MGA",
                monthlyPrice = "25000",
                quarterlyPrice = "65000",
                yearlyPrice = "220000",
                transferInstructions = "Dial #111# and include your account email in the transfer note."
            ).isSuccess
        )
        assertTrue(
            adminRepo.updatePaymentMethodConfig(
                actingAdminId = admin.id,
                methodCode = MobileMoneyMethod.AIRTEL_MONEY.code,
                isEnabled = true,
                receivingAccountNumber = "+261 33 44 555 66",
                receivingAccountName = "Interna Education Airtel",
                currency = "MGA",
                monthlyPrice = "25000",
                quarterlyPrice = "65000",
                yearlyPrice = "220000",
                transferInstructions = "Dial *436# and keep your SMS reference."
            ).isSuccess
        )
        assertTrue(
            adminRepo.updatePaymentMethodConfig(
                actingAdminId = admin.id,
                methodCode = MobileMoneyMethod.ORANGE_MONEY.code,
                isEnabled = true,
                receivingAccountNumber = "+261 32 77 888 99",
                receivingAccountName = "Interna Education Orange",
                currency = "MGA",
                monthlyPrice = "25000",
                quarterlyPrice = "65000",
                yearlyPrice = "220000",
                transferInstructions = "Dial #144# and submit the Orange Money transaction ID."
            ).isSuccess
        )

        // 3. Underpayment and future payment dates are rejected
        assertTrue(
            quotaRepo.submitMobileMoneyPaymentRequest(
                userId = learnerYas.id,
                requestedPlan = PremiumPlanOption.MONTHLY.planCode,
                paymentMethod = MobileMoneyMethod.YAS_MONEY.code,
                transactionReference = "YAS-UNDERPAY-01",
                amountPaid = "15000",
                paymentDate = paymentDateToday
            ).isFailure
        )
        assertTrue(
            quotaRepo.submitMobileMoneyPaymentRequest(
                userId = learnerYas.id,
                requestedPlan = PremiumPlanOption.MONTHLY.planCode,
                paymentMethod = MobileMoneyMethod.YAS_MONEY.code,
                transactionReference = "YAS-FUTURE-01",
                amountPaid = "25000",
                paymentDate = "2099-01-01"
            ).isFailure
        )

        // 4. Learner 1 submits Yas Money (Monthly 30d) WITH proof screenshot -> stays strictly PENDING!
        val yasReq = quotaRepo.submitMobileMoneyPaymentRequest(
            userId = learnerYas.id,
            requestedPlan = PremiumPlanOption.MONTHLY.planCode,
            paymentMethod = MobileMoneyMethod.YAS_MONEY.code,
            transactionReference = "YAS-TX-900101",
            amountPaid = "25000",
            paymentDate = paymentDateToday,
            proofOfPaymentUri = "content://media/picker/yas_receipt_900101.jpg",
            optionalMessage = "Paid via Yas Money"
        ).getOrThrow()
        assertEquals("PENDING", yasReq.status)
        val yasPendingSnap = quotaRepo.getEffectiveQuotaSnapshot(learnerYas.id)
        assertFalse(yasPendingSnap.isPremiumActive)
        assertEquals("PENDING", yasPendingSnap.subscriptionStatus)

        // 5. Duplicate/replayed transaction reference (even spaced/lowercase) by another user is blocked
        assertTrue(
            quotaRepo.submitMobileMoneyPaymentRequest(
                userId = learnerAirtel.id,
                requestedPlan = PremiumPlanOption.QUARTERLY.planCode,
                paymentMethod = MobileMoneyMethod.AIRTEL_MONEY.code,
                transactionReference = "yas-tx-900 101",
                amountPaid = "65000",
                paymentDate = paymentDateToday
            ).isFailure
        )

        // 6. Learner 2 submits Airtel Money (Quarterly 90d) -> stays PENDING until approved -> unlocks 90 days
        val airtelReq = quotaRepo.submitMobileMoneyPaymentRequest(
            userId = learnerAirtel.id,
            requestedPlan = PremiumPlanOption.QUARTERLY.planCode,
            paymentMethod = MobileMoneyMethod.AIRTEL_MONEY.code,
            transactionReference = "AIRTEL-TX-778899",
            amountPaid = "65000",
            paymentDate = paymentDateToday
        ).getOrThrow()
        assertFalse(quotaRepo.getEffectiveQuotaSnapshot(learnerAirtel.id).isPremiumActive)

        assertTrue(adminRepo.approveSubscriptionRequest(admin.id, airtelReq.id).isSuccess)
        // Duplicate approval of already-approved request is blocked
        assertTrue(adminRepo.approveSubscriptionRequest(admin.id, airtelReq.id).isFailure)

        val airtelApprovedSnap = quotaRepo.getEffectiveQuotaSnapshot(learnerAirtel.id)
        assertTrue(airtelApprovedSnap.isPremiumActive)
        assertEquals(PremiumPlanOption.QUARTERLY.planCode, airtelApprovedSnap.activePlanCode)
        assertEquals(90, airtelApprovedSnap.subscriptionDurationDays)
        assertTrue(airtelApprovedSnap.expirationDateMillis > simulatedNowMillis)

        // 7. Learner 3 submits Orange Money (Yearly 365d) -> Admin REJECTS -> does NOT unlock Premium
        val orangeBadReq = quotaRepo.submitMobileMoneyPaymentRequest(
            userId = learnerOrange.id,
            requestedPlan = PremiumPlanOption.YEARLY.planCode,
            paymentMethod = MobileMoneyMethod.ORANGE_MONEY.code,
            transactionReference = "OM-UNVERIFIED-000",
            amountPaid = "220000",
            paymentDate = paymentDateToday
        ).getOrThrow()
        assertTrue(adminRepo.rejectSubscriptionRequest(admin.id, orangeBadReq.id, "Reference not found in Orange Money ledger").isSuccess)
        val orangeRejectedSnap = quotaRepo.getEffectiveQuotaSnapshot(learnerOrange.id)
        assertFalse(orangeRejectedSnap.isPremiumActive)
        assertEquals("NONE", orangeRejectedSnap.subscriptionStatus)
        assertEquals("REJECTED", dao.getSubscriptionRequestById(orangeBadReq.id)!!.status)

        // Learner 3 submits verified Orange Money transfer -> Admin approves -> unlocks 365 days
        val orangeValidReq = quotaRepo.submitMobileMoneyPaymentRequest(
            userId = learnerOrange.id,
            requestedPlan = PremiumPlanOption.YEARLY.planCode,
            paymentMethod = MobileMoneyMethod.ORANGE_MONEY.code,
            transactionReference = "OM-VERIFIED-365",
            amountPaid = "220000",
            paymentDate = paymentDateToday
        ).getOrThrow()
        assertTrue(adminRepo.approveSubscriptionRequest(admin.id, orangeValidReq.id).isSuccess)
        val orangeApprovedSnap = quotaRepo.getEffectiveQuotaSnapshot(learnerOrange.id)
        assertTrue(orangeApprovedSnap.isPremiumActive)
        assertEquals(PremiumPlanOption.YEARLY.planCode, orangeApprovedSnap.activePlanCode)
        assertEquals(365, orangeApprovedSnap.subscriptionDurationDays)

        // 8. Admin approves Learner 1's Yas Money Monthly (30d) -> unlocks 30 days, then expires after 31 days
        assertTrue(adminRepo.approveSubscriptionRequest(admin.id, yasReq.id).isSuccess)
        val yasApprovedSnap = quotaRepo.getEffectiveQuotaSnapshot(learnerYas.id)
        assertTrue(yasApprovedSnap.isPremiumActive)
        assertEquals(PremiumPlanOption.MONTHLY.planCode, yasApprovedSnap.activePlanCode)
        assertEquals(30, yasApprovedSnap.subscriptionDurationDays)

        simulatedNowMillis += 31L * 86_400_000L
        val yasExpiredSnap = quotaRepo.getEffectiveQuotaSnapshot(learnerYas.id)
        assertFalse(yasExpiredSnap.isPremiumActive)
        assertEquals("EXPIRED", yasExpiredSnap.subscriptionStatus)
        // Meanwhile Quarterly (90d) and Yearly (365d) learners remain active at day 31!
        assertTrue(quotaRepo.getEffectiveQuotaSnapshot(learnerAirtel.id).isPremiumActive)
        assertTrue(quotaRepo.getEffectiveQuotaSnapshot(learnerOrange.id).isPremiumActive)

        val auditLogs = dao.observeAuditLogs().first()
        assertTrue(auditLogs.any { it.actionType == "PAYMENT_METHOD_CONFIG_UPDATED" && it.succeeded })
        assertTrue(auditLogs.any { it.actionType == "SUBSCRIPTION_APPROVED" && it.succeeded })
        assertTrue(auditLogs.any { it.actionType == "SUBSCRIPTION_REJECTED" && it.succeeded })
    }

    @Test
    fun `daily usage quota enforces 20 free turns and cannot be bypassed by repository refresh`() = runBlocking {
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        authRepo.ensureSeedDataInitialized()
        dao.upsertApplicationSettings(
            ApplicationSettingsEntity(freeOfflineDailyTurnsLimit = 20, rateLimitRequestsPerMinute = 100)
        )

        val quotaRepo1 = QuotaAndSubscriptionRepository(dao) { simulatedNowMillis }
        val learner = authRepo.registerUser(
            "Marco", "marco@example.com", "MarcoPass#123", "en", "it", "Beginner", "Culture"
        ).getOrThrow().user

        repeat(20) {
            assertTrue(quotaRepo1.checkCanExecuteConversationTurn(learner.id).isSuccess)
            quotaRepo1.recordConversationUsage(learner.id, elapsedActiveSeconds = 15L, estimatedTokens = 120L, succeeded = true)
        }

        assertTrue(quotaRepo1.checkCanExecuteConversationTurn(learner.id).isFailure)

        val quotaRepoReloaded = QuotaAndSubscriptionRepository(dao) { simulatedNowMillis }
        assertEquals(0, quotaRepoReloaded.getEffectiveQuotaSnapshot(learner.id).remainingFreeTurns)
        assertTrue(quotaRepoReloaded.checkCanExecuteConversationTurn(learner.id).isFailure)

        simulatedNowMillis += 25L * 3600L * 1000L
        assertEquals(20, quotaRepoReloaded.getEffectiveQuotaSnapshot(learner.id).remainingFreeTurns)
        assertTrue(quotaRepoReloaded.checkCanExecuteConversationTurn(learner.id).isSuccess)
    }

    @Test
    fun `offline mode blocks cloud AI honestly while keeping lessons cached dictionary and user isolation intact`() = runBlocking {
        var online = false
        val authRepo = AuthAndUserRepository(dao, crypto, gateway) { simulatedNowMillis }
        val quotaRepo = QuotaAndSubscriptionRepository(dao) { simulatedNowMillis }
        val learningRepo = LanguageLearningRepository(
            dao = dao,
            geminiClient = GeminiApiClient(apiKeyProvider = { "test-key" }, backendUrlProvider = { "DIRECT_GEMINI_MODE" }),
            dictionaryClient = DictionaryApiClient(),
            quotaRepo = quotaRepo,
            isOnlineProvider = { online },
            isLocalModelAvailableProvider = { false },
            clockMillis = { simulatedNowMillis }
        )
        authRepo.ensureSeedDataInitialized()

        val userA = authRepo.registerUser("User A", "usera@example.com", "PasswordA#123", "en", "fr", "Beginner", "Travel").getOrThrow().user
        val userB = authRepo.registerUser("User B", "userb@example.com", "PasswordB#123", "en", "ja", "Beginner", "Travel").getOrThrow().user

        val session = learningRepo.startConversationSession(userA.id, "fr", "fr-FR", "free_talk", "Beginner").getOrThrow()
        val offlineTurn = learningRepo.sendConversationMessage(userA.id, session, "Bonjour", true, emptyList())
        assertTrue(offlineTurn.isFailure)
        assertTrue(offlineTurn.exceptionOrNull()!!.message!!.contains("Offline", ignoreCase = true))

        val cachedLookup = learningRepo.lookupDictionary("bonjour", "fr", "en")
        assertTrue(cachedLookup.isSuccess)
        val bonjourEntry = cachedLookup.getOrThrow()
        assertEquals("bonjour", bonjourEntry.word)
        assertEquals(null, bonjourEntry.verifiedAudioUrl)

        learningRepo.saveWordToVocabulary(userA.id, bonjourEntry, markDifficult = true)
        assertEquals(1, dao.observeSavedVocabulary(userA.id).first().size)
        assertEquals(0, dao.observeSavedVocabulary(userB.id).first().size)

        val firstLesson = dao.observeAllOfflineLessons().first().first()
        learningRepo.completeLessonQuiz(userA.id, firstLesson, firstLesson.quizCorrectIndex)
        assertEquals(1, dao.getUnsyncedProgress(userA.id).size)

        online = true
        val synced = learningRepo.synchronizeOfflineProgressIfOnline(userA.id)
        assertEquals(1, synced)
        assertEquals(0, dao.getUnsyncedProgress(userA.id).size)
    }

    @Test
    fun `non destructive migration 1 to 2 upgrades v1 tables and preserves existing data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "interna_migration_v1_v2_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `subscription_requests` (
                                `id` TEXT NOT NULL,
                                `userId` TEXT NOT NULL,
                                `userEmail` TEXT NOT NULL,
                                `userName` TEXT NOT NULL,
                                `requestedPlan` TEXT NOT NULL,
                                `optionalMessage` TEXT NOT NULL,
                                `status` TEXT NOT NULL,
                                `rejectionReason` TEXT NOT NULL,
                                `durationDays` INTEGER NOT NULL,
                                `reviewedByAdminId` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `reviewedAt` INTEGER NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `application_settings` (
                                `singletonId` TEXT NOT NULL,
                                `freeOfflineDailyTurnsLimit` INTEGER NOT NULL,
                                `premiumOnlineDailyMinutesLimit` INTEGER NOT NULL,
                                `supportedLanguagesCsv` TEXT NOT NULL,
                                `maxConversationTurnsPerSession` INTEGER NOT NULL,
                                `aiConversationEnabled` INTEGER NOT NULL,
                                `dictionaryAiEnabled` INTEGER NOT NULL,
                                `pronunciationCoachEnabled` INTEGER NOT NULL,
                                `defaultSubscriptionDurationDays` INTEGER NOT NULL,
                                `maintenanceMode` INTEGER NOT NULL,
                                `accountSuspensionErrorThreshold` INTEGER NOT NULL,
                                `rateLimitRequestsPerMinute` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                PRIMARY KEY(`singletonId`)
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val writable = helper.writableDatabase
        writable.execSQL(
            "INSERT INTO `subscription_requests` VALUES ('req_v1', 'usr_v1', 'v1@example.com', 'V1 Learner', 'PREMIUM_ONLINE', 'Legacy request', 'PENDING', '', 30, '', 1760000000000, 0)"
        )
        writable.execSQL(
            "INSERT INTO `application_settings` VALUES ('GLOBAL_CONFIG', 20, 120, 'en,fr,es', 50, 1, 1, 1, 30, 0, 25, 15, 1760000000000)"
        )

        // Execute MIGRATION_1_2
        InternaDatabase.MIGRATION_1_2.migrate(writable)

        // Verify v1 rows survived and gained v2 columns cleanly
        writable.query("SELECT id, userEmail, verificationMode, paymentMethod FROM subscription_requests WHERE id = 'req_v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("req_v1", cursor.getString(0))
            assertEquals("v1@example.com", cursor.getString(1))
            assertEquals("MANUAL_VERIFICATION", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }
        writable.query("SELECT singletonId, adminProvisionTokenConsumed FROM application_settings WHERE singletonId = 'GLOBAL_CONFIG'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GLOBAL_CONFIG", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        writable.query("SELECT name FROM sqlite_master WHERE type='table' AND name='payment_method_configs'").use { cursor ->
            assertNotNull(cursor)
            assertTrue(cursor.moveToFirst())
        }

        helper.close()
        context.deleteDatabase(dbName)
    }
}
