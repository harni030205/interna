package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AdminAuditLogEntity
import com.example.data.local.ApplicationSettingsEntity
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.ConversationSessionEntity
import com.example.data.local.DictionaryCacheEntity
import com.example.data.local.FeedbackEntity
import com.example.data.local.InternaDatabase
import com.example.data.local.LearningProgressEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.OfflineLessonEntity
import com.example.data.local.PaymentMethodConfigEntity
import com.example.data.local.SavedVocabularyEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.SubscriptionRequestEntity
import com.example.data.local.SurveyEntity
import com.example.data.local.SurveyResponseEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserPreferenceEntity
import com.example.data.remote.DictionaryApiClient
import com.example.data.remote.GeminiApiClient
import com.example.data.repository.AdminConsoleRepository
import com.example.data.repository.AdminOverviewMetrics
import com.example.data.repository.AdminUserSummaryRow
import com.example.data.repository.AuthAndUserRepository
import com.example.data.repository.LanguageLearningRepository
import com.example.data.repository.PasswordResetChallenge
import com.example.data.repository.QuotaAndSubscriptionRepository
import com.example.data.repository.QuotaStatusSnapshot
import com.example.data.repository.SessionStore
import com.example.domain.model.DictionaryResultModel
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.PronunciationFeedbackModel
import com.example.domain.model.SessionSummaryReport
import com.example.domain.model.TranslationResultModel
import com.example.security.CryptoSecurityManager
import com.example.service.NetworkAndOfflineModelMonitor
import com.example.service.VoiceSpeechController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppDestination {
    WELCOME,
    SIGN_IN,
    SIGN_UP,
    PASSWORD_RECOVERY,
    DEVELOPER_AUTH,
    HOME,
    AI_CONVERSATION,
    CONVERSATION_SUMMARY,
    DICTIONARY,
    TRANSLATION,
    PRONUNCIATION,
    PRACTICE,
    PROGRESS,
    SUBSCRIPTION,
    SETTINGS,
    DEVELOPER_CONSOLE
}

enum class DeveloperSubTab {
    OVERVIEW,
    USERS,
    SUBSCRIPTIONS,
    SURVEYS_FEEDBACK,
    USAGE_CONTROLS,
    AUDIT_LOG
}

@OptIn(ExperimentalCoroutinesApi::class)
class InternaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = InternaDatabase.getInstance(application)
    private val dao = database.dao()
    val crypto = CryptoSecurityManager()
    private val sessionStore = SessionStore(application)
    val networkMonitor = NetworkAndOfflineModelMonitor(application)
    val voiceController = VoiceSpeechController(application)
    val geminiClient = GeminiApiClient()
    private val dictionaryClient = DictionaryApiClient()

    val authRepo = AuthAndUserRepository(dao, crypto)
    val quotaRepo = QuotaAndSubscriptionRepository(dao)
    val learningRepo = LanguageLearningRepository(
        dao = dao,
        geminiClient = geminiClient,
        dictionaryClient = dictionaryClient,
        quotaRepo = quotaRepo,
        isOnlineProvider = { networkMonitor.isOnline.value },
        isLocalModelAvailableProvider = { networkMonitor.isLocalOnDeviceModelAvailable() }
    )
    val adminRepo = AdminConsoleRepository(dao, authRepo, quotaRepo)

    private val _currentDestination = MutableStateFlow(AppDestination.WELCOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _developerSubTab = MutableStateFlow(DeveloperSubTab.OVERVIEW)
    val developerSubTab: StateFlow<DeveloperSubTab> = _developerSubTab.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val activeUserId: StateFlow<String?> = sessionStore.activeUserIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentUser: StateFlow<UserEntity?> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else dao.observeUserById(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userPreferences: StateFlow<UserPreferenceEntity?> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else dao.observePreferences(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isVerifiedAdmin: StateFlow<Boolean> = combine(
        currentUser,
        activeUserId.flatMapLatest { uid -> if (uid == null) flowOf(null) else dao.observeRolePermission(uid) }
    ) { user, rolePerm ->
        if (user == null || rolePerm == null) false
        else {
            user.accountStatus == "ACTIVE" &&
                user.emailVerified &&
                user.email.equals(CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL, ignoreCase = true) &&
                rolePerm.role == "ADMIN" &&
                crypto.verifyRoleGrantSignature(user.id, user.email, rolePerm.role, rolePerm.grantSignature)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _quotaSnapshot = MutableStateFlow<QuotaStatusSnapshot?>(null)
    val quotaSnapshot: StateFlow<QuotaStatusSnapshot?> = _quotaSnapshot.asStateFlow()

    val paymentMethodConfigs: StateFlow<List<PaymentMethodConfigEntity>> = quotaRepo.observePaymentMethodConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSubscription: StateFlow<SubscriptionEntity?> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else quotaRepo.observeUserSubscription(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userSubscriptionRequests: StateFlow<List<SubscriptionRequestEntity>> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else quotaRepo.observeUserRequests(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else dao.observeUserNotifications(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learningProgress: StateFlow<List<LearningProgressEntity>> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else dao.observeUserProgress(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedVocabulary: StateFlow<List<SavedVocabularyEntity>> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else dao.observeSavedVocabulary(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineLessons: StateFlow<List<OfflineLessonEntity>> = dao.observeAllOfflineLessons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDictionaryCache: StateFlow<List<DictionaryCacheEntity>> = dao.observeRecentDictionaryCache()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publishedSurveys: StateFlow<List<SurveyEntity>> = dao.observePublishedSurveys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<ApplicationSettingsEntity> = dao.observeApplicationSettings()
        .combine(flowOf(ApplicationSettingsEntity())) { dbVal, fallback -> dbVal ?: fallback }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApplicationSettingsEntity())

    private val _dispatchedCodeHint = MutableStateFlow<String?>(null)
    val dispatchedCodeHint: StateFlow<String?> = _dispatchedCodeHint.asStateFlow()

    private val _resetChallenge = MutableStateFlow<PasswordResetChallenge?>(null)
    val resetChallenge: StateFlow<PasswordResetChallenge?> = _resetChallenge.asStateFlow()

    private val _activeSession = MutableStateFlow<ConversationSessionEntity?>(null)
    val activeSession: StateFlow<ConversationSessionEntity?> = _activeSession.asStateFlow()

    private val _inMemoryMessages = MutableStateFlow<List<ConversationMessageEntity>>(emptyList())
    val activeMessages: StateFlow<List<ConversationMessageEntity>> = _inMemoryMessages.asStateFlow()

    private val _latestSessionSummary = MutableStateFlow<SessionSummaryReport?>(null)
    val latestSessionSummary: StateFlow<SessionSummaryReport?> = _latestSessionSummary.asStateFlow()

    val pastSessions: StateFlow<List<ConversationSessionEntity>> = activeUserId
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else dao.observeUserSessions(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dictionaryResult = MutableStateFlow<DictionaryResultModel?>(null)
    val dictionaryResult: StateFlow<DictionaryResultModel?> = _dictionaryResult.asStateFlow()

    private val _translationResult = MutableStateFlow<TranslationResultModel?>(null)
    val translationResult: StateFlow<TranslationResultModel?> = _translationResult.asStateFlow()

    private val _pronunciationResult = MutableStateFlow<PronunciationFeedbackModel?>(null)
    val pronunciationResult: StateFlow<PronunciationFeedbackModel?> = _pronunciationResult.asStateFlow()

    private val _exportedDataJson = MutableStateFlow<String?>(null)
    val exportedDataJson: StateFlow<String?> = _exportedDataJson.asStateFlow()

    private val _adminOverview = MutableStateFlow<AdminOverviewMetrics?>(null)
    val adminOverview: StateFlow<AdminOverviewMetrics?> = _adminOverview.asStateFlow()

    private val _adminUsersList = MutableStateFlow<List<AdminUserSummaryRow>>(emptyList())
    val adminUsersList: StateFlow<List<AdminUserSummaryRow>> = _adminUsersList.asStateFlow()

    val adminAllRequests: StateFlow<List<SubscriptionRequestEntity>> = isVerifiedAdmin
        .flatMapLatest { isAdmin -> if (isAdmin) dao.observeAllSubscriptionRequests() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminAllSurveys: StateFlow<List<SurveyEntity>> = isVerifiedAdmin
        .flatMapLatest { isAdmin -> if (isAdmin) dao.observeAllSurveys() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminAllSurveyResponses: StateFlow<List<SurveyResponseEntity>> = isVerifiedAdmin
        .flatMapLatest { isAdmin -> if (isAdmin) dao.observeAllSurveyResponses() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminAllFeedback: StateFlow<List<FeedbackEntity>> = isVerifiedAdmin
        .flatMapLatest { isAdmin -> if (isAdmin) dao.observeAllFeedback() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminAuditLogs: StateFlow<List<AdminAuditLogEntity>> = isVerifiedAdmin
        .flatMapLatest { isAdmin -> if (isAdmin) dao.observeAuditLogs() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            authRepo.ensureSeedDataInitialized()
        }
        viewModelScope.launch {
            combine(activeUserId, sessionStore.onboardingCompletedFlow) { uid, onboarded ->
                uid to onboarded
            }.collect { (uid, onboarded) ->
                if (uid != null) {
                    refreshQuotaSnapshot(uid)
                    if (_currentDestination.value in setOf(
                            AppDestination.WELCOME,
                            AppDestination.SIGN_IN,
                            AppDestination.SIGN_UP,
                            AppDestination.PASSWORD_RECOVERY
                        )
                    ) {
                        _currentDestination.value = AppDestination.HOME
                    }
                } else {
                    _quotaSnapshot.value = null
                    if (_currentDestination.value !in setOf(
                            AppDestination.WELCOME,
                            AppDestination.SIGN_IN,
                            AppDestination.SIGN_UP,
                            AppDestination.PASSWORD_RECOVERY,
                            AppDestination.DEVELOPER_AUTH
                        )
                    ) {
                        _currentDestination.value = if (onboarded) AppDestination.SIGN_IN else AppDestination.WELCOME
                    }
                }
            }
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                val uid = activeUserId.value
                if (online && uid != null) {
                    val syncedCount = learningRepo.synchronizeOfflineProgressIfOnline(uid)
                    if (syncedCount > 0) {
                        _statusBanner.value = "Synchronized $syncedCount offline study record(s)."
                    }
                }
            }
        }
    }

    fun clearBanners() {
        _statusBanner.value = null
        _errorBanner.value = null
    }

    fun postError(msg: String) {
        _errorBanner.value = msg
    }

    fun navigateTo(destination: AppDestination) {
        clearBanners()
        if (destination == AppDestination.DEVELOPER_CONSOLE && !isVerifiedAdmin.value) {
            _errorBanner.value = "Access denied: Only verified administrators can open Developer Console."
            return
        }
        _currentDestination.value = destination
        if (destination == AppDestination.DEVELOPER_CONSOLE) {
            refreshAdminDashboard()
        }
        activeUserId.value?.let { uid ->
            viewModelScope.launch { refreshQuotaSnapshot(uid) }
        }
    }

    fun selectDeveloperSubTab(tab: DeveloperSubTab) {
        _developerSubTab.value = tab
        refreshAdminDashboard()
    }

    suspend fun refreshQuotaSnapshot(uid: String) {
        _quotaSnapshot.value = quotaRepo.getEffectiveQuotaSnapshot(uid)
    }

    fun completeOnboardingAndOpenSignUp() {
        viewModelScope.launch {
            sessionStore.setOnboardingCompleted(true)
            _currentDestination.value = AppDestination.SIGN_UP
        }
    }

    fun registerAccount(
        fullName: String,
        email: String,
        password: String,
        nativeLang: String,
        targetLang: String,
        level: String,
        goal: String
    ) {
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            authRepo.registerUser(fullName, email, password, nativeLang, targetLang, level, goal)
                .onSuccess { outcome ->
                    sessionStore.setOnboardingCompleted(true)
                    sessionStore.setActiveUserId(outcome.user.id)
                    _dispatchedCodeHint.value = outcome.verificationCodeDispatchPreview
                    _statusBanner.value = "Account created! Verification code: ${outcome.verificationCodeDispatchPreview} (verify in Settings)."
                    _currentDestination.value = AppDestination.HOME
                }
                .onFailure { _errorBanner.value = it.message ?: "Registration failed." }
            _isBusy.value = false
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            authRepo.signIn(email, password)
                .onSuccess { user ->
                    sessionStore.setOnboardingCompleted(true)
                    sessionStore.setActiveUserId(user.id)
                    if (authRepo.isVerifiedAdministrator(user.id)) {
                        _statusBanner.value = "Signed in as Verified Administrator. Private Developer Console is ready."
                    }
                    _currentDestination.value = AppDestination.HOME
                }
                .onFailure { _errorBanner.value = it.message ?: "Sign-in failed." }
            _isBusy.value = false
        }
    }

    fun signInDeveloper(email: String, password: String) {
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            authRepo.signInDeveloper(email, password)
                .onSuccess { adminUser ->
                    sessionStore.setOnboardingCompleted(true)
                    sessionStore.setActiveUserId(adminUser.id)
                    _statusBanner.value = "Authenticated as Administrator (${adminUser.email})."
                    _currentDestination.value = AppDestination.DEVELOPER_CONSOLE
                    adminRepo.getOverviewMetrics(adminUser.id).onSuccess { _adminOverview.value = it }
                    adminRepo.searchUsers(adminUser.id, "", "ALL", 0).onSuccess { _adminUsersList.value = it }
                }
                .onFailure { _errorBanner.value = it.message ?: "Developer sign-in failed." }
            _isBusy.value = false
        }
    }

    fun bootstrapOrUpgradeDeveloperAccount(
        email: String,
        password: String,
        fullName: String,
        provisionToken: String
    ) {
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            authRepo.bootstrapOrUpgradeDeveloperAccount(email, password, fullName, provisionToken)
                .onSuccess { adminUser ->
                    sessionStore.setOnboardingCompleted(true)
                    sessionStore.setActiveUserId(adminUser.id)
                    _statusBanner.value = "Initial administrator provisioned and one-time token locked. Welcome to Developer Console."
                    _currentDestination.value = AppDestination.DEVELOPER_CONSOLE
                    adminRepo.getOverviewMetrics(adminUser.id).onSuccess { _adminOverview.value = it }
                    adminRepo.searchUsers(adminUser.id, "", "ALL", 0).onSuccess { _adminUsersList.value = it }
                }
                .onFailure { _errorBanner.value = it.message ?: "Administrator provisioning failed." }
            _isBusy.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _activeSession.value = null
            _inMemoryMessages.value = emptyList()
            sessionStore.setActiveUserId(null)
            _currentDestination.value = AppDestination.SIGN_IN
        }
    }

    fun verifyEmailCode(code: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            authRepo.verifyEmail(uid, code)
                .onSuccess {
                    _dispatchedCodeHint.value = null
                    _statusBanner.value = "Email address verified!"
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun resendVerificationCode() {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            authRepo.resendEmailVerificationCode(uid)
                .onSuccess { code ->
                    _dispatchedCodeHint.value = code
                    _statusBanner.value = "Verification code generated: $code"
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            clearBanners()
            authRepo.requestPasswordReset(email)
                .onSuccess { challenge ->
                    _resetChallenge.value = challenge
                    _statusBanner.value = "Recovery code issued (${challenge.resetCodeDispatchPreview}). Valid for 15 minutes."
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            clearBanners()
            authRepo.confirmPasswordReset(email, code, newPassword)
                .onSuccess {
                    _resetChallenge.value = null
                    _statusBanner.value = "Password updated! Please sign in with your new password."
                    _currentDestination.value = AppDestination.SIGN_IN
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun provisionDeveloperRole(provisionToken: String) {
        val uid = activeUserId.value
        if (uid == null) {
            _errorBanner.value = "Sign in to your verified account first before activating Developer Console."
            return
        }
        viewModelScope.launch {
            clearBanners()
            authRepo.provisionAuthorizedAdministrator(uid, provisionToken)
                .onSuccess {
                    _statusBanner.value = "Administrator role cryptographically verified. Developer Console unlocked."
                    _currentDestination.value = AppDestination.DEVELOPER_CONSOLE
                    refreshAdminDashboard()
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun startNewConversation(languageCode: String, accentTag: String, scenarioId: String, difficulty: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            clearBanners()
            learningRepo.startConversationSession(uid, languageCode, accentTag, scenarioId, difficulty)
                .onSuccess { session ->
                    _activeSession.value = session
                    val langName = LanguageCatalog.byCode(languageCode).englishName
                    val welcome = ConversationMessageEntity(
                        id = "welcome_${session.id}",
                        sessionId = session.id,
                        userId = uid,
                        sender = "AI",
                        content = "Hello! I'm Interna AI. Ready to practice $langName ($difficulty)? Share whatever is on your mind or dive straight into the scenario."
                    )
                    _inMemoryMessages.value = listOf(welcome)
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun sendChatTurn(userText: String, explainInNative: Boolean, autoSpeakReply: Boolean) {
        val uid = activeUserId.value ?: return
        val session = _activeSession.value ?: return
        val currentList = _inMemoryMessages.value
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            learningRepo.sendConversationMessage(uid, session, userText, explainInNative, currentList)
                .onSuccess { (aiTurn, updatedSession) ->
                    _activeSession.value = updatedSession
                    val userMsg = ConversationMessageEntity(
                        id = "u_${System.currentTimeMillis()}",
                        sessionId = session.id,
                        userId = uid,
                        sender = "USER",
                        content = userText.trim()
                    )
                    val aiMsg = ConversationMessageEntity(
                        id = "a_${System.currentTimeMillis() + 1}",
                        sessionId = session.id,
                        userId = uid,
                        sender = "AI",
                        content = aiTurn.reply,
                        grammarCorrection = aiTurn.grammarCorrection,
                        vocabularySuggestions = aiTurn.vocabularySuggestions.joinToString(" • "),
                        naturalAlternative = aiTurn.naturalAlternative,
                        nativeExplanation = aiTurn.nativeLanguageExplanation
                    )
                    _inMemoryMessages.value = currentList + userMsg + aiMsg
                    refreshQuotaSnapshot(uid)
                    if (autoSpeakReply) {
                        voiceController.speakText(aiTurn.reply, session.accentTag)
                    }
                }
                .onFailure {
                    refreshQuotaSnapshot(uid)
                    _errorBanner.value = it.message ?: "Conversation turn failed."
                }
            _isBusy.value = false
        }
    }

    fun finishActiveConversation() {
        val uid = activeUserId.value ?: return
        val session = _activeSession.value ?: return
        val msgs = _inMemoryMessages.value
        viewModelScope.launch {
            _isBusy.value = true
            learningRepo.finishAndSummarizeSession(uid, session, msgs)
                .onSuccess { report ->
                    _latestSessionSummary.value = report
                    _activeSession.value = null
                    _currentDestination.value = AppDestination.CONVERSATION_SUMMARY
                }
                .onFailure { _errorBanner.value = it.message }
            _isBusy.value = false
        }
    }

    fun searchDictionary(query: String, sourceLang: String, targetLang: String) {
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            learningRepo.lookupDictionary(query, sourceLang, targetLang)
                .onSuccess { _dictionaryResult.value = it }
                .onFailure { _errorBanner.value = it.message }
            _isBusy.value = false
        }
    }

    fun saveCurrentDictionaryWord(markDifficult: Boolean) {
        val uid = activeUserId.value ?: return
        val entry = _dictionaryResult.value ?: return
        viewModelScope.launch {
            learningRepo.saveWordToVocabulary(uid, entry, markDifficult)
                .onSuccess { _statusBanner.value = "Saved '${it.word}' to your personal vocabulary & flashcards." }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun saveSummaryVocabItem(term: String, meaning: String, example: String, targetLang: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            val entity = SavedVocabularyEntity(
                id = "voc_${uid}_${term.lowercase().hashCode()}",
                userId = uid,
                word = term,
                sourceLanguage = targetLang,
                targetLanguage = currentUser.value?.nativeLanguage ?: "en",
                translation = meaning,
                partOfSpeech = "phrase",
                ipaPhonetic = null,
                audioUrl = null,
                definition = meaning,
                exampleSentence = example
            )
            dao.upsertSavedVocabulary(entity)
            _statusBanner.value = "Added '$term' to Flashcards."
        }
    }

    fun reviewFlashcard(item: SavedVocabularyEntity, rememberedWell: Boolean) {
        viewModelScope.launch {
            learningRepo.reviewFlashcard(item, rememberedWell)
        }
    }

    fun deleteVocabulary(vocabId: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch { dao.deleteVocabularyItem(vocabId, uid) }
    }

    fun runTranslation(text: String, sourceLang: String, targetLang: String, tone: String, contextHint: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            learningRepo.translateWorkspace(uid, text, sourceLang, targetLang, tone, contextHint)
                .onSuccess { _translationResult.value = it }
                .onFailure { _errorBanner.value = it.message }
            _isBusy.value = false
        }
    }

    fun evaluatePronunciation(languageCode: String, targetPhrase: String, spokenTranscript: String) {
        viewModelScope.launch {
            clearBanners()
            _isBusy.value = true
            learningRepo.evaluatePronunciationAttempt(languageCode, targetPhrase, spokenTranscript)
                .onSuccess { _pronunciationResult.value = it }
                .onFailure { _errorBanner.value = it.message }
            _isBusy.value = false
        }
    }

    fun toggleOfflineLessonDownload(lesson: OfflineLessonEntity) {
        viewModelScope.launch {
            dao.setLessonDownloaded(lesson.id, !lesson.isDownloadedOffline)
        }
    }

    fun submitLessonQuizAnswer(lesson: OfflineLessonEntity, selectedIndex: Int) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            learningRepo.completeLessonQuiz(uid, lesson, selectedIndex)
                .onSuccess { correct ->
                    _statusBanner.value = if (correct) {
                        "Correct! +25 XP recorded (${if (networkMonitor.isOnline.value) "synced" else "saved offline for sync"})."
                    } else {
                        "Completed review (+10 XP). Check the grammar explanation below."
                    }
                }
        }
    }

    fun manualSyncProgress() {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            if (!networkMonitor.isOnline.value) {
                _errorBanner.value = "Device is offline. Progress is safely stored locally and will sync when reconnected."
                return@launch
            }
            val count = learningRepo.synchronizeOfflineProgressIfOnline(uid)
            _statusBanner.value = if (count > 0) "Synchronized $count offline record(s)." else "All learning progress is up to date."
        }
    }

    fun submitMobileMoneyPayment(
        requestedPlan: String,
        paymentMethod: String,
        transactionReference: String,
        amountPaid: String,
        paymentDate: String,
        proofOfPaymentUri: String,
        optionalMessage: String
    ) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            clearBanners()
            quotaRepo.submitMobileMoneyPaymentRequest(
                userId = uid,
                requestedPlan = requestedPlan,
                paymentMethod = paymentMethod,
                transactionReference = transactionReference,
                amountPaid = amountPaid,
                paymentDate = paymentDate,
                proofOfPaymentUri = proofOfPaymentUri,
                optionalMessage = optionalMessage
            ).onSuccess { req ->
                refreshQuotaSnapshot(uid)
                _statusBanner.value = "Payment request ${req.id} saved as PENDING. Premium unlocks once verified and approved by the administrator."
            }.onFailure { _errorBanner.value = it.message }
        }
    }

    fun updateUserProfile(
        fullName: String,
        nativeLang: String,
        targetLangsCsv: String,
        activeTargetLang: String,
        level: String,
        goal: String,
        dailyMinutes: Int
    ) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            authRepo.updateProfile(uid, fullName, nativeLang, targetLangsCsv, activeTargetLang, level, goal, dailyMinutes)
                .onSuccess { _statusBanner.value = "Profile & learning preferences saved." }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun updatePreferences(
        interfaceLanguage: String? = null,
        themeMode: String? = null,
        saveHistory: Boolean? = null,
        nativeExplanations: Boolean? = null
    ) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            val base = userPreferences.value ?: UserPreferenceEntity(userId = uid)
            authRepo.updatePreferences(
                base.copy(
                    interfaceLanguage = interfaceLanguage ?: base.interfaceLanguage,
                    themeMode = themeMode ?: base.themeMode,
                    saveConversationHistory = saveHistory ?: base.saveConversationHistory,
                    nativeExplanationsEnabled = nativeExplanations ?: base.nativeExplanationsEnabled
                )
            )
        }
    }

    fun submitSurveyAnswer(surveyId: String, mcChoice: String, freeText: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            learningRepo.submitSurveyResponse(uid, surveyId, mcChoice, freeText)
                .onSuccess { _statusBanner.value = "Thank you! Your survey response was recorded." }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun submitFeedbackReport(category: String, subject: String, message: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            learningRepo.submitUserFeedback(uid, category, subject, message)
                .onSuccess { _statusBanner.value = "Feedback submitted to the Interna engineering team." }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun markNotificationsRead() {
        val uid = activeUserId.value ?: return
        viewModelScope.launch { dao.markAllNotificationsRead(uid) }
    }

    fun exportUserDataJson() {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            authRepo.exportPersonalDataJson(uid)
                .onSuccess { _exportedDataJson.value = it }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun clearExportedJson() {
        _exportedDataJson.value = null
    }

    fun deleteAccountPermanently(currentPassword: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            authRepo.deleteAccountAndPersonalData(uid, currentPassword)
                .onSuccess {
                    sessionStore.setActiveUserId(null)
                    _statusBanner.value = "Your account and personal records have been permanently deleted."
                    _currentDestination.value = AppDestination.WELCOME
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun refreshAdminDashboard(searchQuery: String = "", statusFilter: String = "ALL", page: Int = 0) {
        val uid = activeUserId.value ?: return
        if (!isVerifiedAdmin.value) return
        viewModelScope.launch {
            adminRepo.getOverviewMetrics(uid).onSuccess { _adminOverview.value = it }
            adminRepo.searchUsers(uid, searchQuery, statusFilter, page).onSuccess { _adminUsersList.value = it }
        }
    }

    fun adminApproveRequest(requestId: String, durationDays: Int, dailyMinutes: Int) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.approveSubscriptionRequest(uid, requestId, durationDays, dailyMinutes)
                .onSuccess {
                    _statusBanner.value = "Approved request $requestId ($durationDays days)."
                    refreshAdminDashboard()
                    refreshQuotaSnapshot(uid)
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminRejectRequest(requestId: String, reason: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.rejectSubscriptionRequest(uid, requestId, reason)
                .onSuccess {
                    _statusBanner.value = "Rejected request $requestId."
                    refreshAdminDashboard()
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminToggleUserSuspension(targetUserId: String, suspend: Boolean) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.setUserSuspensionStatus(uid, targetUserId, suspend)
                .onSuccess {
                    _statusBanner.value = if (suspend) "Account suspended." else "Account restored."
                    refreshAdminDashboard()
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminChangeUserPlan(targetUserId: String, grantPremium: Boolean, days: Int = 30) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.changeUserPlanDirectly(uid, targetUserId, grantPremium, days)
                .onSuccess {
                    _statusBanner.value = "Updated user access plan."
                    refreshAdminDashboard()
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminResetUserQuota(targetUserId: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.resetUserDailyQuota(uid, targetUserId)
                .onSuccess {
                    _statusBanner.value = "Reset user daily usage quota."
                    refreshAdminDashboard()
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminUpdateUserLimitAndNote(targetUserId: String, customLimit: Int?, note: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.setUserCustomDailyAllowance(uid, targetUserId, customLimit, note)
                .onSuccess {
                    _statusBanner.value = "Saved user limit & administrative note."
                    refreshAdminDashboard()
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminSaveGlobalControls(settings: ApplicationSettingsEntity) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.updateGlobalUsageControls(uid, settings)
                .onSuccess {
                    _statusBanner.value = "Global usage & service controls updated."
                    refreshQuotaSnapshot(uid)
                }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminCreateSurvey(title: String, desc: String, mcQ: String, mcOpts: List<String>, freeQ: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.createOrUpdateSurvey(uid, title, desc, mcQ, mcOpts, freeQ, true)
                .onSuccess { _statusBanner.value = "Survey '${it.title}' published." }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminToggleSurveyStatus(survey: SurveyEntity, publish: Boolean) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.setSurveyPublished(uid, survey, publish)
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminSavePaymentMethodConfig(
        methodCode: String,
        isEnabled: Boolean,
        accountNumber: String,
        accountName: String,
        currency: String,
        monthlyPrice: String,
        quarterlyPrice: String,
        yearlyPrice: String,
        instructions: String
    ) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            clearBanners()
            adminRepo.updatePaymentMethodConfig(
                actingAdminId = uid,
                methodCode = methodCode,
                isEnabled = isEnabled,
                receivingAccountNumber = accountNumber,
                receivingAccountName = accountName,
                currency = currency,
                monthlyPrice = monthlyPrice,
                quarterlyPrice = quarterlyPrice,
                yearlyPrice = yearlyPrice,
                transferInstructions = instructions
            ).onSuccess { cfg ->
                _statusBanner.value = "${cfg.displayName} payment details saved and published."
            }.onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminUpdateFeedbackStatus(feedbackId: String, newStatus: String) {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.updateSupportMessageStatus(uid, feedbackId, newStatus)
                .onFailure { _errorBanner.value = it.message }
        }
    }

    fun adminExportReport() {
        val uid = activeUserId.value ?: return
        viewModelScope.launch {
            adminRepo.exportMinimizedAdminReportJson(uid)
                .onSuccess { _exportedDataJson.value = it }
                .onFailure { _errorBanner.value = it.message }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.shutdown()
        voiceController.shutdown()
    }
}
