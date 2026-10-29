package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.AdminAuditLogEntity
import com.example.data.local.ApplicationSettingsEntity
import com.example.data.local.InternaDao
import com.example.data.local.NotificationEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.SeedContentProvider
import com.example.data.local.UserEntity
import com.example.data.local.UserPreferenceEntity
import com.example.data.remote.BackendSecurityGatewayClient
import com.example.security.CryptoSecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "interna_session_store")

class SessionStore(private val context: Context) {
    private val keyActiveUserId = stringPreferencesKey("active_user_id")
    private val keyOnboardingDone = booleanPreferencesKey("onboarding_completed")

    val activeUserIdFlow: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[keyActiveUserId]?.takeIf { it.isNotBlank() }
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.sessionDataStore.data.map { prefs ->
        prefs[keyOnboardingDone] ?: false
    }

    suspend fun setActiveUserId(userId: String?) {
        context.sessionDataStore.edit { prefs ->
            if (userId == null) prefs.remove(keyActiveUserId) else prefs[keyActiveUserId] = userId
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[keyOnboardingDone] = completed
        }
    }
}

data class RegistrationOutcome(
    val user: UserEntity,
    val verificationCodeDispatchPreview: String
)

data class PasswordResetChallenge(
    val email: String,
    val resetCodeDispatchPreview: String,
    val expiresAtMillis: Long
)

class AuthAndUserRepository(
    private val dao: InternaDao,
    private val crypto: CryptoSecurityManager,
    private val backendGateway: BackendSecurityGatewayClient = BackendSecurityGatewayClient(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun ensureSeedDataInitialized() {
        if (dao.getApplicationSettings() == null) {
            dao.upsertApplicationSettings(ApplicationSettingsEntity(updatedAt = clockMillis()))
        }
        dao.insertDefaultPaymentMethodConfigs(SeedContentProvider.defaultPaymentMethodConfigs())
        if (dao.countOfflineLessons() == 0) {
            dao.insertOfflineLessons(SeedContentProvider.defaultOfflineLessons())
            for (entry in SeedContentProvider.defaultCachedDictionaryEntries()) {
                dao.upsertDictionaryCache(entry)
            }
            dao.upsertSurvey(SeedContentProvider.initialWelcomeSurvey())
        }
    }

    suspend fun registerUser(
        fullName: String,
        email: String,
        password: String,
        nativeLanguage: String,
        primaryTargetLanguage: String,
        proficiencyLevel: String,
        learningGoal: String
    ): Result<RegistrationOutcome> = runCatching {
        val cleanName = fullName.trim()
        val normalizedEmail = email.trim().lowercase()
        require(cleanName.length in 2..80) { "Full name must be between 2 and 80 characters." }
        require(isValidEmail(normalizedEmail)) { "Please enter a valid email address." }
        require(password.length >= 8) { "Password must be at least 8 characters long." }

        val existing = dao.getUserByEmail(normalizedEmail)
        require(existing == null) { "An account with this email address already exists." }

        val hashed = crypto.hashPassword(password.toCharArray())
        val verificationCode = crypto.generateNumericCode(6)
        val verificationHash = crypto.sha256Hex(verificationCode)
        val userId = "usr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = clockMillis()

        val newUser = UserEntity(
            id = userId,
            fullName = cleanName,
            email = normalizedEmail,
            passwordHash = hashed.hashBase64,
            passwordSalt = hashed.saltBase64,
            emailVerified = false,
            verificationCodeHash = verificationHash,
            nativeLanguage = nativeLanguage,
            targetLanguagesCsv = primaryTargetLanguage,
            activeTargetLanguage = primaryTargetLanguage,
            proficiencyLevel = proficiencyLevel,
            learningGoal = learningGoal,
            createdAt = now,
            lastActiveAt = now
        )
        dao.insertUser(newUser)

        // CRITICAL SECURITY RULE: Every newly registered account—even harinirinaianjara@gmail.com—
        // starts strictly as USER. Administrator role is NEVER granted merely by entering an email.
        val userRoleSig = crypto.signRoleGrant(userId, normalizedEmail, "USER")
        dao.upsertRolePermission(
            RolePermissionEntity(
                userId = userId,
                role = "USER",
                grantedBy = "SYSTEM_DEFAULT",
                grantSignature = userRoleSig,
                grantedAt = now
            )
        )

        dao.upsertPreferences(
            UserPreferenceEntity(
                userId = userId,
                interfaceLanguage = "en",
                themeMode = "DARK",
                updatedAt = now
            )
        )

        dao.insertNotification(
            NotificationEntity(
                id = "notif_welcome_$userId",
                userId = userId,
                title = "Welcome to Interna",
                body = "One World. Every Language. Verify your email in Settings to secure your account.",
                type = "SYSTEM",
                createdAt = now
            )
        )

        RegistrationOutcome(user = newUser, verificationCodeDispatchPreview = verificationCode)
    }

    suspend fun signIn(email: String, password: String): Result<UserEntity> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        val user = dao.getUserByEmail(normalizedEmail)
            ?: error("Invalid email address or password.")

        if (user.accountStatus == "SUSPENDED") {
            error("This account has been suspended by an administrator.")
        }

        val ok = crypto.verifyPassword(password.toCharArray(), user.passwordHash, user.passwordSalt)
        if (!ok) {
            error("Invalid email address or password.")
        }

        val updated = user.copy(lastActiveAt = clockMillis())
        dao.updateUser(updated)
        updated
    }

    /**
     * Dedicated Developer Login: authenticates email and password and strictly requires
     * cryptographically verified administrator privileges before allowing console access.
     */
    suspend fun signInDeveloper(email: String, password: String): Result<UserEntity> = runCatching {
        val user = signIn(email, password).getOrThrow()
        if (!isVerifiedAdministrator(user.id)) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = user.id,
                    adminEmail = user.email,
                    actionType = "DEVELOPER_LOGIN_DENIED_NON_ADMIN",
                    affectedTarget = "DEVELOPER_CONSOLE",
                    sanitizedSummary = "Blocked Developer Login attempt from non-administrator account.",
                    succeeded = false,
                    timestamp = clockMillis()
                )
            )
            error("Access denied: This account is not provisioned as an authorized administrator.")
        }
        user
    }

    /**
     * Secure, one-time initial administrator setup for harinirinaianjara@gmail.com:
     * - Does NOT require registering as a normal user first: if the developer account does not
     *   exist yet, creates it directly with a PBKDF2-HMAC-SHA256 hashed password.
     * - If harinirinaianjara@gmail.com already exists, verifies its password and assigns
     *   administrator privileges in-place WITHOUT creating a duplicate account.
     * - Requires the secret ADMIN_PROVISION_TOKEN (and remote HTTPS backend approval when
     *   INTERNA_BACKEND_URL is an https:// endpoint), then permanently locks the one-time
     *   provisioning flag so the token cannot be reused.
     */
    suspend fun bootstrapOrUpgradeDeveloperAccount(
        email: String,
        password: String,
        fullName: String = "Interna Administrator",
        suppliedProvisionToken: String
    ): Result<UserEntity> = runCatching {
        ensureSeedDataInitialized()
        backendGateway.validateBackendConfigurationSafety().getOrThrow()

        val now = clockMillis()
        val normalizedEmail = email.trim().lowercase()
        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity(updatedAt = now)

        if (settings.adminProvisionTokenConsumed) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = "PROVISION_LOCKED",
                    adminEmail = normalizedEmail.ifBlank { "unknown" },
                    actionType = "ADMIN_PROVISION_TOKEN_REUSE_BLOCKED",
                    affectedTarget = normalizedEmail.ifBlank { "DEVELOPER_SETUP" },
                    sanitizedSummary = "Rejected attempt to reuse one-time initial administrator provisioning token.",
                    succeeded = false,
                    timestamp = now
                )
            )
            error("Initial administrator provisioning has already been completed. The one-time provisioning token cannot be reused.")
        }

        if (!normalizedEmail.equals(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = "UNAUTHORIZED_BOOTSTRAP",
                    adminEmail = normalizedEmail.ifBlank { "unknown" },
                    actionType = "ADMIN_PROVISION_REJECTED_UNAUTHORIZED_EMAIL",
                    affectedTarget = normalizedEmail.ifBlank { "unknown" },
                    sanitizedSummary = "Blocked initial administrator setup attempt for unauthorized email.",
                    succeeded = false,
                    timestamp = now
                )
            )
            error("This email address is not authorized for administrator provisioning.")
        }

        if (!crypto.isAdminProvisioningConfigured()) {
            error("ADMIN_PROVISION_TOKEN is not configured with a strong secret yet. Configure it in the AI Studio Secrets panel first.")
        }

        if (!crypto.verifyOneTimeProvisionSecret(normalizedEmail, suppliedProvisionToken)) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = "INVALID_TOKEN",
                    adminEmail = normalizedEmail,
                    actionType = "ADMIN_PROVISION_INVALID_SECRET",
                    affectedTarget = normalizedEmail,
                    sanitizedSummary = "Invalid one-time ADMIN_PROVISION_TOKEN supplied during initial developer setup.",
                    succeeded = false,
                    timestamp = now
                )
            )
            error("Invalid ADMIN_PROVISION_TOKEN.")
        }

        backendGateway.verifyInitialAdminProvisionOnBackendIfConfigured(
            email = normalizedEmail,
            suppliedProvisionToken = suppliedProvisionToken
        ).getOrThrow()

        require(password.length >= 8) { "Administrator password must be at least 8 characters long." }

        val existingUser = dao.getUserByEmail(normalizedEmail)
        val isNewAccount: Boolean
        val targetUser: UserEntity

        if (existingUser == null) {
            isNewAccount = true
            val hashed = crypto.hashPassword(password.toCharArray())
            val userId = "usr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            targetUser = UserEntity(
                id = userId,
                fullName = fullName.trim().ifBlank { "Interna Administrator" }.take(80),
                email = normalizedEmail,
                passwordHash = hashed.hashBase64,
                passwordSalt = hashed.saltBase64,
                emailVerified = true,
                verificationCodeHash = "",
                accountStatus = "ACTIVE",
                createdAt = now,
                lastActiveAt = now
            )
        } else {
            isNewAccount = false
            require(existingUser.accountStatus != "SUSPENDED") {
                "Existing account is suspended and cannot be provisioned."
            }
            val passwordMatches = crypto.verifyPassword(
                password.toCharArray(),
                existingUser.passwordHash,
                existingUser.passwordSalt
            )
            require(passwordMatches) {
                "An account for $normalizedEmail already exists. Enter its current password (or reset it via Password Recovery) to assign administrator privileges without creating a duplicate account."
            }
            targetUser = existingUser.copy(
                emailVerified = true,
                verificationCodeHash = "",
                accountStatus = "ACTIVE",
                lastActiveAt = now
            )
        }

        val adminSig = crypto.signRoleGrant(targetUser.id, targetUser.email, "ADMIN")
        val roleGrant = RolePermissionEntity(
            userId = targetUser.id,
            role = "ADMIN",
            grantedBy = "ONE_TIME_PROVISION_TOKEN",
            grantSignature = adminSig,
            grantedAt = now
        )
        val audit = AdminAuditLogEntity(
            id = "aud_${UUID.randomUUID()}",
            adminId = targetUser.id,
            adminEmail = targetUser.email,
            actionType = "ADMIN_ROLE_PROVISIONED",
            affectedTarget = targetUser.email,
            sanitizedSummary = if (isNewAccount) {
                "Provisioned new initial administrator account and locked one-time provisioning token."
            } else {
                "Upgraded existing account to initial administrator without duplication and locked one-time provisioning token."
            },
            succeeded = true,
            timestamp = now
        )

        val committed = dao.consumeProvisionTokenAndGrantAdminAtomically(
            userToCreateOrUpdate = targetUser,
            isNewAccount = isNewAccount,
            roleGrant = roleGrant,
            auditLog = audit,
            nowMillis = now
        )
        if (!committed) {
            error("One-time administrator provisioning token was already consumed.")
        }
        targetUser
    }

    suspend fun verifyEmail(userId: String, code: String): Result<UserEntity> = runCatching {
        val user = dao.getUserById(userId) ?: error("Account not found.")
        if (user.emailVerified) return@runCatching user

        require(crypto.verifyCodeHash(code, user.verificationCodeHash)) {
            "Invalid verification code. Please check the 6-digit code and try again."
        }

        val updated = user.copy(emailVerified = true, verificationCodeHash = "")
        dao.updateUser(updated)
        updated
    }

    suspend fun resendEmailVerificationCode(userId: String): Result<String> = runCatching {
        val user = dao.getUserById(userId) ?: error("Account not found.")
        val newCode = crypto.generateNumericCode(6)
        dao.updateUser(user.copy(verificationCodeHash = crypto.sha256Hex(newCode)))
        newCode
    }

    suspend fun requestPasswordReset(email: String): Result<PasswordResetChallenge> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        val user = dao.getUserByEmail(normalizedEmail)
            ?: error("No active account found for that email address.")

        val code = crypto.generateNumericCode(6)
        val expiresAt = clockMillis() + 15L * 60L * 1000L // 15-minute expiry
        dao.updateUser(
            user.copy(
                resetTokenHash = crypto.sha256Hex(code),
                resetTokenExpiresAt = expiresAt
            )
        )
        PasswordResetChallenge(
            email = normalizedEmail,
            resetCodeDispatchPreview = code,
            expiresAtMillis = expiresAt
        )
    }

    suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        require(newPassword.length >= 8) { "New password must be at least 8 characters long." }
        val normalizedEmail = email.trim().lowercase()
        val user = dao.getUserByEmail(normalizedEmail)
            ?: error("Account not found.")

        require(user.resetTokenHash.isNotBlank() && clockMillis() <= user.resetTokenExpiresAt) {
            "Recovery code has expired. Please request a new recovery code."
        }
        require(crypto.verifyCodeHash(code, user.resetTokenHash)) {
            "Invalid recovery code."
        }

        val hashed = crypto.hashPassword(newPassword.toCharArray())
        dao.updateUser(
            user.copy(
                passwordHash = hashed.hashBase64,
                passwordSalt = hashed.saltBase64,
                resetTokenHash = "",
                resetTokenExpiresAt = 0L
            )
        )
    }

    /**
     * Provisions an already-authenticated, email-verified harinirinaianjara@gmail.com account
     * using the one-time ADMIN_PROVISION_TOKEN and locks reuse after success.
     */
    suspend fun provisionAuthorizedAdministrator(
        userId: String,
        suppliedProvisionToken: String
    ): Result<Unit> = runCatching {
        ensureSeedDataInitialized()
        backendGateway.validateBackendConfigurationSafety().getOrThrow()

        val user = dao.getUserById(userId) ?: error("Authenticated user not found.")
        val now = clockMillis()
        val settings = dao.getApplicationSettings() ?: ApplicationSettingsEntity(updatedAt = now)

        if (settings.adminProvisionTokenConsumed) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = userId,
                    adminEmail = user.email,
                    actionType = "ADMIN_PROVISION_TOKEN_REUSE_BLOCKED",
                    affectedTarget = user.email,
                    sanitizedSummary = "Blocked attempt to reuse already-consumed ADMIN_PROVISION_TOKEN.",
                    succeeded = false,
                    timestamp = now
                )
            )
            error("One-time administrator provisioning token has already been used and cannot be reused.")
        }

        if (!user.email.equals(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = userId,
                    adminEmail = user.email,
                    actionType = "ADMIN_PROVISION_REJECTED_UNAUTHORIZED_EMAIL",
                    affectedTarget = user.email,
                    sanitizedSummary = "Blocked administrator provisioning attempt from non-authorized email.",
                    succeeded = false,
                    timestamp = now
                )
            )
            error("This email address is not authorized for initial administrator provisioning.")
        }

        if (!user.emailVerified) {
            error("Verify your email address first before provisioning administrator privileges.")
        }

        if (!crypto.isAdminProvisioningConfigured()) {
            error("ADMIN_PROVISION_TOKEN is not configured yet. Set a strong secret in the Google AI Studio Secrets panel first.")
        }

        val validToken = crypto.verifyAdminProvisionToken(
            normalizedEmail = user.email,
            emailVerified = user.emailVerified,
            suppliedToken = suppliedProvisionToken
        )

        if (!validToken) {
            dao.insertAuditLog(
                AdminAuditLogEntity(
                    id = "aud_${UUID.randomUUID()}",
                    adminId = userId,
                    adminEmail = user.email,
                    actionType = "ADMIN_PROVISION_INVALID_SECRET",
                    affectedTarget = user.email,
                    sanitizedSummary = "Invalid ADMIN_PROVISION_TOKEN supplied during provisioning.",
                    succeeded = false,
                    timestamp = now
                )
            )
            error("Invalid ADMIN_PROVISION_TOKEN.")
        }

        backendGateway.verifyInitialAdminProvisionOnBackendIfConfigured(
            email = user.email,
            suppliedProvisionToken = suppliedProvisionToken
        ).getOrThrow()

        val adminSig = crypto.signRoleGrant(user.id, user.email, "ADMIN")
        val roleGrant = RolePermissionEntity(
            userId = user.id,
            role = "ADMIN",
            grantedBy = "ONE_TIME_PROVISION_TOKEN",
            grantSignature = adminSig,
            grantedAt = now
        )
        val audit = AdminAuditLogEntity(
            id = "aud_${UUID.randomUUID()}",
            adminId = user.id,
            adminEmail = user.email,
            actionType = "ADMIN_ROLE_PROVISIONED",
            affectedTarget = user.email,
            sanitizedSummary = "Cryptographically verified and provisioned initial administrator role (one-time token locked).",
            succeeded = true,
            timestamp = now
        )

        val committed = dao.consumeProvisionTokenAndGrantAdminAtomically(
            userToCreateOrUpdate = user.copy(lastActiveAt = now),
            isNewAccount = false,
            roleGrant = roleGrant,
            auditLog = audit,
            nowMillis = now
        )
        if (!committed) {
            error("One-time administrator provisioning token has already been used.")
        }
    }

    suspend fun isVerifiedAdministrator(userId: String): Boolean {
        val user = dao.getUserById(userId) ?: return false
        if (user.accountStatus != "ACTIVE" || !user.emailVerified) return false
        if (!user.email.equals(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)) {
            return false
        }
        val rolePerm = dao.getRolePermission(userId) ?: return false
        if (rolePerm.role != "ADMIN") return false
        return crypto.verifyRoleGrantSignature(user.id, user.email, rolePerm.role, rolePerm.grantSignature)
    }

    suspend fun updateProfile(
        userId: String,
        fullName: String,
        nativeLanguage: String,
        targetLanguagesCsv: String,
        activeTargetLanguage: String,
        proficiencyLevel: String,
        learningGoal: String,
        dailyStudyMinutesGoal: Int
    ): Result<UserEntity> = runCatching {
        val user = dao.getUserById(userId) ?: error("Account not found.")
        require(fullName.trim().length in 2..80) { "Name must be 2–80 characters." }
        val updated = user.copy(
            fullName = fullName.trim(),
            nativeLanguage = nativeLanguage,
            targetLanguagesCsv = targetLanguagesCsv.ifBlank { activeTargetLanguage },
            activeTargetLanguage = activeTargetLanguage,
            proficiencyLevel = proficiencyLevel,
            learningGoal = learningGoal,
            dailyStudyMinutesGoal = dailyStudyMinutesGoal.coerceIn(5, 240),
            lastActiveAt = clockMillis()
        )
        dao.updateUser(updated)
        updated
    }

    suspend fun updatePreferences(preferences: UserPreferenceEntity) {
        dao.upsertPreferences(preferences.copy(updatedAt = clockMillis()))
    }

    suspend fun exportPersonalDataJson(userId: String): Result<String> = runCatching {
        val user = dao.getUserById(userId) ?: error("User not found.")
        val vocab = dao.getSavedVocabularyList(userId)
        val progress = dao.getUserProgressList(userId)
        val sub = dao.getSubscriptionForUser(userId)

        JSONObject().apply {
            put("exportedAtMillis", clockMillis())
            put(
                "profile",
                JSONObject().apply {
                    put("id", user.id)
                    put("fullName", user.fullName)
                    put("email", user.email)
                    put("emailVerified", user.emailVerified)
                    put("nativeLanguage", user.nativeLanguage)
                    put("targetLanguages", user.targetLanguagesCsv)
                    put("proficiencyLevel", user.proficiencyLevel)
                    put("learningGoal", user.learningGoal)
                }
            )
            put("subscriptionPlan", sub?.planType ?: "FREE_OFFLINE")
            put("subscriptionStatus", sub?.status ?: "NONE")
            put(
                "savedVocabulary",
                JSONArray().apply {
                    vocab.forEach { v ->
                        put(
                            JSONObject().apply {
                                put("word", v.word)
                                put("translation", v.translation)
                                put("language", v.sourceLanguage)
                                put("masteryLevel", v.masteryLevel)
                            }
                        )
                    }
                }
            )
            put("completedLessonsCount", progress.size)
            put("totalXpEarned", progress.sumOf { it.xpEarned })
        }.toString(2)
    }

    suspend fun deleteAccountAndPersonalData(userId: String, passwordConfirmation: String): Result<Unit> = runCatching {
        val user = dao.getUserById(userId) ?: error("User not found.")
        require(crypto.verifyPassword(passwordConfirmation.toCharArray(), user.passwordHash, user.passwordSalt)) {
            "Incorrect password confirmation. Account was not deleted."
        }
        dao.purgeAllUserData(userId)
    }

    private fun isValidEmail(email: String): Boolean {
        return email.length in 5..120 &&
            email.contains("@") &&
            email.substringAfter("@").contains(".") &&
            !email.contains(" ")
    }
}
