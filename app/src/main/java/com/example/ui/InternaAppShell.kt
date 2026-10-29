package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ConnectivityBadge
import com.example.ui.components.FeedbackBannerBar
import com.example.ui.i18n.InterfaceLocalizer
import com.example.ui.screens.AiConversationScreen
import com.example.ui.screens.ConversationSummaryScreen
import com.example.ui.screens.DeveloperAuthProvisionScreen
import com.example.ui.screens.DeveloperConsoleScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.PasswordRecoveryScreen
import com.example.ui.screens.PracticeAndQuizzesScreen
import com.example.ui.screens.ProgressDashboardScreen
import com.example.ui.screens.PronunciationCoachScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.SignUpScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.TranslationWorkspaceScreen
import com.example.ui.screens.UniversalDictionaryScreen
import com.example.ui.screens.WelcomeOnboardingScreen

private data class NavEntry(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternaAppShell(viewModel: InternaViewModel) {
    val context = LocalContext.current
    val currentDest by viewModel.currentDestination.collectAsStateWithLifecycle()
    val devSubTab by viewModel.developerSubTab.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isVerifiedAdmin by viewModel.isVerifiedAdmin.collectAsStateWithLifecycle()
    val isOnline by viewModel.networkMonitor.isOnline.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceController.isListening.collectAsStateWithLifecycle()

    val quotaSnapshot by viewModel.quotaSnapshot.collectAsStateWithLifecycle()
    val paymentConfigs by viewModel.paymentMethodConfigs.collectAsStateWithLifecycle()
    val userSubscription by viewModel.userSubscription.collectAsStateWithLifecycle()
    val userRequests by viewModel.userSubscriptionRequests.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val progressList by viewModel.learningProgress.collectAsStateWithLifecycle()
    val savedVocab by viewModel.savedVocabulary.collectAsStateWithLifecycle()
    val offlineLessons by viewModel.offlineLessons.collectAsStateWithLifecycle()
    val dictCache by viewModel.recentDictionaryCache.collectAsStateWithLifecycle()
    val publishedSurveys by viewModel.publishedSurveys.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    val dispatchedCodeHint by viewModel.dispatchedCodeHint.collectAsStateWithLifecycle()
    val resetChallenge by viewModel.resetChallenge.collectAsStateWithLifecycle()

    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val latestSummary by viewModel.latestSessionSummary.collectAsStateWithLifecycle()
    val pastSessions by viewModel.pastSessions.collectAsStateWithLifecycle()

    val dictResult by viewModel.dictionaryResult.collectAsStateWithLifecycle()
    val translationResult by viewModel.translationResult.collectAsStateWithLifecycle()
    val pronunciationResult by viewModel.pronunciationResult.collectAsStateWithLifecycle()
    val exportedJson by viewModel.exportedDataJson.collectAsStateWithLifecycle()

    val adminOverview by viewModel.adminOverview.collectAsStateWithLifecycle()
    val adminUsers by viewModel.adminUsersList.collectAsStateWithLifecycle()
    val adminAllRequests by viewModel.adminAllRequests.collectAsStateWithLifecycle()
    val adminSurveys by viewModel.adminAllSurveys.collectAsStateWithLifecycle()
    val adminSurveyResponses by viewModel.adminAllSurveyResponses.collectAsStateWithLifecycle()
    val adminFeedback by viewModel.adminAllFeedback.collectAsStateWithLifecycle()
    val adminAuditLogs by viewModel.adminAuditLogs.collectAsStateWithLifecycle()

    val uiStrings = remember(userPrefs?.interfaceLanguage) {
        InterfaceLocalizer.stringsFor(userPrefs?.interfaceLanguage ?: "en")
    }

    var showMoreToolsSheet by rememberSaveable { mutableStateOf(false) }

    val unauthenticatedRoutes = setOf(
        AppDestination.WELCOME,
        AppDestination.SIGN_IN,
        AppDestination.SIGN_UP,
        AppDestination.PASSWORD_RECOVERY
    )

    if (currentDest in unauthenticatedRoutes || (currentDest == AppDestination.DEVELOPER_AUTH && currentUser == null)) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                FeedbackBannerBar(
                    statusMessage = statusBanner,
                    errorMessage = errorBanner,
                    onDismiss = viewModel::clearBanners
                )
                when (currentDest) {
                    AppDestination.WELCOME -> WelcomeOnboardingScreen(
                        onGetStartedSignUp = viewModel::completeOnboardingAndOpenSignUp,
                        onExistingSignIn = { viewModel.navigateTo(AppDestination.SIGN_IN) }
                    )
                    AppDestination.SIGN_UP -> SignUpScreen(
                        isBusy = isBusy,
                        onRegister = viewModel::registerAccount,
                        onNavigateToSignIn = { viewModel.navigateTo(AppDestination.SIGN_IN) },
                        onBack = { viewModel.navigateTo(AppDestination.WELCOME) }
                    )
                    AppDestination.SIGN_IN -> SignInScreen(
                        isBusy = isBusy,
                        onSignIn = viewModel::signIn,
                        onNavigateToSignUp = { viewModel.navigateTo(AppDestination.SIGN_UP) },
                        onNavigateToPasswordRecovery = { viewModel.navigateTo(AppDestination.PASSWORD_RECOVERY) },
                        onNavigateToDeveloperLogin = { viewModel.navigateTo(AppDestination.DEVELOPER_AUTH) },
                        onBack = { viewModel.navigateTo(AppDestination.WELCOME) }
                    )
                    AppDestination.PASSWORD_RECOVERY -> PasswordRecoveryScreen(
                        challenge = resetChallenge,
                        onRequestCode = viewModel::requestPasswordReset,
                        onConfirmReset = viewModel::confirmPasswordReset,
                        onBackToSignIn = { viewModel.navigateTo(AppDestination.SIGN_IN) }
                    )
                    AppDestination.DEVELOPER_AUTH -> DeveloperAuthProvisionScreen(
                        currentUser = null,
                        isProvisionTokenConsumed = appSettings.adminProvisionTokenConsumed,
                        isAlreadyVerifiedAdmin = false,
                        isBusy = isBusy,
                        onDeveloperSignIn = viewModel::signInDeveloper,
                        onBootstrapOrUpgradeAdmin = viewModel::bootstrapOrUpgradeDeveloperAccount,
                        onVerifyEmailCode = viewModel::verifyEmailCode,
                        onResendVerificationCode = viewModel::resendVerificationCode,
                        onProvisionSignedInAdmin = viewModel::provisionDeveloperRole,
                        onOpenDeveloperConsole = { viewModel.navigateTo(AppDestination.DEVELOPER_CONSOLE) },
                        onNavigateToPasswordRecovery = { viewModel.navigateTo(AppDestination.PASSWORD_RECOVERY) },
                        onBack = { viewModel.navigateTo(AppDestination.SIGN_IN) }
                    )
                    else -> Unit
                }
            }
        }
        return
    }

    if (currentDest != AppDestination.HOME) {
        BackHandler {
            viewModel.navigateTo(AppDestination.HOME)
        }
    }

    val allSections = buildList {
        add(NavEntry(AppDestination.HOME, uiStrings.navHome, Icons.Default.Home, "nav_home"))
        add(NavEntry(AppDestination.AI_CONVERSATION, uiStrings.navConversation, Icons.Default.Forum, "nav_conversation"))
        add(NavEntry(AppDestination.PRACTICE, uiStrings.navPractice, Icons.Default.School, "nav_practice"))
        add(NavEntry(AppDestination.DICTIONARY, uiStrings.navDictionary, Icons.AutoMirrored.Filled.MenuBook, "nav_dictionary"))
        add(NavEntry(AppDestination.TRANSLATION, uiStrings.navTranslation, Icons.Default.GTranslate, "nav_translation"))
        add(NavEntry(AppDestination.PRONUNCIATION, uiStrings.navPronunciation, Icons.Default.RecordVoiceOver, "nav_pronunciation"))
        add(NavEntry(AppDestination.PROGRESS, uiStrings.navProgress, Icons.Default.Insights, "nav_progress"))
        add(NavEntry(AppDestination.SUBSCRIPTION, uiStrings.navSubscription, Icons.Default.WorkspacePremium, "nav_subscription"))
        add(NavEntry(AppDestination.SETTINGS, uiStrings.navSettings, Icons.Default.Settings, "nav_settings"))
        if (isVerifiedAdmin) {
            add(NavEntry(AppDestination.DEVELOPER_CONSOLE, uiStrings.navDeveloperConsole, Icons.Default.Shield, "nav_developer_console"))
        }
    }

    val primaryMobileDestinations = allSections.take(4)
    val secondaryMobileDestinations = allSections.drop(4)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiStrings.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiStrings.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        ConnectivityBadge(
                            isOnline = isOnline,
                            onlineText = uiStrings.onlineLabel,
                            offlineText = uiStrings.offlineLabel
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = viewModel::signOut,
                            modifier = Modifier.testTag("topbar_signout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = uiStrings.signOut
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        primaryMobileDestinations.forEach { entry ->
                            NavigationBarItem(
                                selected = currentDest == entry.destination,
                                onClick = { viewModel.navigateTo(entry.destination) },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = { Text(entry.label, maxLines = 1) },
                                modifier = Modifier.testTag(entry.tag)
                            )
                        }
                        val inSecondary = secondaryMobileDestinations.any { it.destination == currentDest }
                        NavigationBarItem(
                            selected = inSecondary,
                            onClick = { showMoreToolsSheet = true },
                            icon = { Icon(Icons.Default.Apps, contentDescription = uiStrings.navMoreHub) },
                            label = { Text(uiStrings.navMoreHub, maxLines = 1) },
                            modifier = Modifier.testTag("nav_more_tools")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    Surface(
                        modifier = Modifier
                            .width(248.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allSections.forEach { entry ->
                                NavigationDrawerItem(
                                    label = { Text(entry.label) },
                                    selected = currentDest == entry.destination,
                                    onClick = { viewModel.navigateTo(entry.destination) },
                                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                                    modifier = Modifier.testTag("sidebar_${entry.tag}")
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    FeedbackBannerBar(
                        statusMessage = statusBanner,
                        errorMessage = errorBanner,
                        onDismiss = viewModel::clearBanners
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentDest) {
                            AppDestination.HOME -> HomeDashboardScreen(
                                user = currentUser,
                                quotaSnapshot = quotaSnapshot,
                                isOnline = isOnline,
                                isGeminiConfigured = viewModel.geminiClient.isConfigured(),
                                progressList = progressList,
                                notifications = notifications,
                                publishedSurveys = publishedSurveys,
                                onNavigate = viewModel::navigateTo,
                                onMarkNotificationsRead = viewModel::markNotificationsRead,
                                onSubmitSurvey = viewModel::submitSurveyAnswer
                            )

                            AppDestination.AI_CONVERSATION -> AiConversationScreen(
                                activeSession = activeSession,
                                messages = activeMessages,
                                quotaSnapshot = quotaSnapshot,
                                isOnline = isOnline,
                                isLocalModelAvailable = viewModel.networkMonitor.isLocalOnDeviceModelAvailable(),
                                isBusy = isBusy,
                                isListening = isListening,
                                defaultLangCode = currentUser?.activeTargetLanguage ?: "fr",
                                supportedLangsCsv = appSettings.supportedLanguagesCsv,
                                onStartSession = viewModel::startNewConversation,
                                onSendMessage = viewModel::sendChatTurn,
                                onSpeakText = { text, accent ->
                                    viewModel.voiceController.speakText(text, accent) { viewModel.postError(it) }
                                },
                                onStartVoiceInput = { accent, onRecognized ->
                                    viewModel.voiceController.startSpeechRecognition(
                                        bcp47Tag = accent,
                                        onResult = onRecognized,
                                        onError = { viewModel.postError(it) }
                                    )
                                },
                                onStopVoiceInput = viewModel.voiceController::stopSpeechRecognition,
                                onFinishSession = viewModel::finishActiveConversation
                            )

                            AppDestination.CONVERSATION_SUMMARY -> ConversationSummaryScreen(
                                report = latestSummary,
                                targetLanguageCode = currentUser?.activeTargetLanguage ?: "fr",
                                onSaveVocab = { term, meaning, example ->
                                    viewModel.saveSummaryVocabItem(
                                        term = term,
                                        meaning = meaning,
                                        example = example,
                                        targetLang = currentUser?.activeTargetLanguage ?: "fr"
                                    )
                                },
                                onDone = { viewModel.navigateTo(AppDestination.HOME) }
                            )

                            AppDestination.DICTIONARY -> UniversalDictionaryScreen(
                                result = dictResult,
                                savedVocabulary = savedVocab,
                                recentCached = dictCache,
                                isBusy = isBusy,
                                onSearch = viewModel::searchDictionary,
                                onSaveWord = viewModel::saveCurrentDictionaryWord,
                                onPlayVerifiedAudio = { url ->
                                    viewModel.voiceController.playRemoteAudioUrl(url) { viewModel.postError(it) }
                                },
                                onSpeakTts = { text, bcp47 ->
                                    viewModel.voiceController.speakText(text, bcp47) { viewModel.postError(it) }
                                },
                                onReviewFlashcard = viewModel::reviewFlashcard,
                                onDeleteVocab = viewModel::deleteVocabulary
                            )

                            AppDestination.TRANSLATION -> TranslationWorkspaceScreen(
                                result = translationResult,
                                isOnline = isOnline,
                                isBusy = isBusy,
                                onTranslate = viewModel::runTranslation,
                                onSpeak = { text, bcp47 ->
                                    viewModel.voiceController.speakText(text, bcp47) { viewModel.postError(it) }
                                }
                            )

                            AppDestination.PRONUNCIATION -> PronunciationCoachScreen(
                                feedback = pronunciationResult,
                                isListening = isListening,
                                isBusy = isBusy,
                                defaultLangCode = currentUser?.activeTargetLanguage ?: "fr",
                                onSpeakReference = { phrase, bcp47 ->
                                    viewModel.voiceController.speakText(phrase, bcp47) { viewModel.postError(it) }
                                },
                                onCaptureSpeech = { bcp47, onRecognized ->
                                    viewModel.voiceController.startSpeechRecognition(
                                        bcp47Tag = bcp47,
                                        onResult = onRecognized,
                                        onError = { viewModel.postError(it) }
                                    )
                                },
                                onEvaluate = viewModel::evaluatePronunciation
                            )

                            AppDestination.PRACTICE -> PracticeAndQuizzesScreen(
                                lessons = offlineLessons,
                                isOnline = isOnline,
                                defaultLangCode = currentUser?.activeTargetLanguage ?: "fr",
                                onToggleDownload = viewModel::toggleOfflineLessonDownload,
                                onSpeakPhrase = { phrase, bcp47 ->
                                    viewModel.voiceController.speakText(phrase, bcp47) { viewModel.postError(it) }
                                },
                                onSubmitQuizAnswer = viewModel::submitLessonQuizAnswer
                            )

                            AppDestination.PROGRESS -> ProgressDashboardScreen(
                                user = currentUser,
                                progressList = progressList,
                                savedVocabulary = savedVocab,
                                pastSessions = pastSessions,
                                isOnline = isOnline,
                                onManualSync = viewModel::manualSyncProgress
                            )

                            AppDestination.SUBSCRIPTION -> SubscriptionScreen(
                                quotaSnapshot = quotaSnapshot,
                                subscription = userSubscription,
                                paymentConfigs = paymentConfigs,
                                requests = userRequests,
                                onSubmitPaymentRequest = viewModel::submitMobileMoneyPayment
                            )

                            AppDestination.SETTINGS -> SettingsScreen(
                                user = currentUser,
                                preferences = userPrefs,
                                dispatchedVerificationHint = dispatchedCodeHint,
                                isVerifiedAdmin = isVerifiedAdmin,
                                onUpdateProfile = viewModel::updateUserProfile,
                                onUpdatePreferences = viewModel::updatePreferences,
                                onVerifyEmail = viewModel::verifyEmailCode,
                                onResendVerificationCode = viewModel::resendVerificationCode,
                                onSubmitFeedback = viewModel::submitFeedbackReport,
                                onExportPersonalData = viewModel::exportUserDataJson,
                                onOpenDeveloperProvisioning = { viewModel.navigateTo(AppDestination.DEVELOPER_AUTH) },
                                onSignOut = viewModel::signOut,
                                onDeleteAccount = viewModel::deleteAccountPermanently
                            )

                            AppDestination.DEVELOPER_AUTH -> DeveloperAuthProvisionScreen(
                                currentUser = currentUser,
                                isProvisionTokenConsumed = appSettings.adminProvisionTokenConsumed,
                                isAlreadyVerifiedAdmin = isVerifiedAdmin,
                                isBusy = isBusy,
                                onDeveloperSignIn = viewModel::signInDeveloper,
                                onBootstrapOrUpgradeAdmin = viewModel::bootstrapOrUpgradeDeveloperAccount,
                                onVerifyEmailCode = viewModel::verifyEmailCode,
                                onResendVerificationCode = viewModel::resendVerificationCode,
                                onProvisionSignedInAdmin = viewModel::provisionDeveloperRole,
                                onOpenDeveloperConsole = { viewModel.navigateTo(AppDestination.DEVELOPER_CONSOLE) },
                                onNavigateToPasswordRecovery = { viewModel.navigateTo(AppDestination.PASSWORD_RECOVERY) },
                                onBack = { viewModel.navigateTo(AppDestination.SETTINGS) }
                            )

                            AppDestination.DEVELOPER_CONSOLE -> DeveloperConsoleScreen(
                                isVerifiedAdmin = isVerifiedAdmin,
                                activeSubTab = devSubTab,
                                overview = adminOverview,
                                usersList = adminUsers,
                                paymentConfigs = paymentConfigs,
                                subscriptionRequests = adminAllRequests,
                                surveys = adminSurveys,
                                surveyResponses = adminSurveyResponses,
                                feedbackItems = adminFeedback,
                                appSettings = appSettings,
                                auditLogs = adminAuditLogs,
                                onSelectSubTab = viewModel::selectDeveloperSubTab,
                                onSearchUsers = { q, st, p -> viewModel.refreshAdminDashboard(q, st, p) },
                                onSavePaymentConfig = viewModel::adminSavePaymentMethodConfig,
                                onApproveSubscription = viewModel::adminApproveRequest,
                                onRejectSubscription = viewModel::adminRejectRequest,
                                onToggleUserSuspension = viewModel::adminToggleUserSuspension,
                                onChangeUserPlan = viewModel::adminChangeUserPlan,
                                onResetUserQuota = viewModel::adminResetUserQuota,
                                onUpdateUserLimitAndNote = viewModel::adminUpdateUserLimitAndNote,
                                onSaveGlobalControls = viewModel::adminSaveGlobalControls,
                                onCreateSurvey = viewModel::adminCreateSurvey,
                                onToggleSurveyStatus = viewModel::adminToggleSurveyStatus,
                                onUpdateFeedbackStatus = viewModel::adminUpdateFeedbackStatus,
                                onExportMinimizedReport = viewModel::adminExportReport
                            )

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    if (showMoreToolsSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreToolsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Interna Workspace & Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                secondaryMobileDestinations.forEach { entry ->
                    ListItem(
                        headlineContent = { Text(entry.label) },
                        leadingContent = {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = entry.label,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                showMoreToolsSheet = false
                                viewModel.navigateTo(entry.destination)
                            }
                            .testTag("sheet_${entry.tag}")
                    )
                }
            }
        }
    }

    if (exportedJson != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearExportedJson,
            title = { Text("Exported Data (JSON)") },
            text = {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = exportedJson.orEmpty(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Interna JSON Export", exportedJson))
                        viewModel.clearExportedJson()
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearExportedJson) {
                    Text("Close")
                }
            }
        )
    }
}
