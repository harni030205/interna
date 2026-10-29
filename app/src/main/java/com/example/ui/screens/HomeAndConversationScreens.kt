package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.ConversationSessionEntity
import com.example.data.local.LearningProgressEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.SurveyEntity
import com.example.data.local.UserEntity
import com.example.data.repository.QuotaStatusSnapshot
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.SessionSummaryReport
import com.example.ui.AppDestination
import com.example.ui.components.FuturisticCard
import com.example.ui.components.LanguageSelectorChips
import com.example.ui.components.QuotaSummaryMeterCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDashboardScreen(
    user: UserEntity?,
    quotaSnapshot: QuotaStatusSnapshot?,
    isOnline: Boolean,
    isGeminiConfigured: Boolean,
    progressList: List<LearningProgressEntity>,
    notifications: List<NotificationEntity>,
    publishedSurveys: List<SurveyEntity>,
    onNavigate: (AppDestination) -> Unit,
    onMarkNotificationsRead: () -> Unit,
    onSubmitSurvey: (surveyId: String, mcOption: String, freeText: String) -> Unit
) {
    val distinctDays = remember(progressList) { progressList.map { it.utcDate }.distinct().size }
    val totalXp = remember(progressList) { progressList.sumOf { it.xpEarned } }
    val unreadNotifs = remember(notifications) { notifications.filter { !it.isRead } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.illustration_hero_world),
                contentDescription = stringResource(R.string.cd_hero_banner),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x55080D1A), Color(0xE6080D1A))
                        )
                    )
                    .padding(18.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = "Welcome, ${user?.fullName ?: "Explorer"}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active Target: ${LanguageCatalog.byCode(user?.activeTargetLanguage ?: "fr").englishName} • ${user?.proficiencyLevel ?: "Intermediate"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFBAE6FD)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusPillBadge(label = "🔥 $distinctDays Study Days", color = AmberWarning)
                        StatusPillBadge(label = "⚡ $totalXp XP", color = EmeraldSuccess)
                    }
                }
            }
        }

        if (!isGeminiConfigured) {
            FuturisticCard(accentBorder = true) {
                Text(
                    text = "Gemini API Key Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "To activate live online Interna AI conversations and contextual translations, open the Secrets panel in Google AI Studio and set GEMINI_API_KEY. Offline lessons, quizzes, flashcards, and verified English dictionary lookups work immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuotaSummaryMeterCard(
            snapshot = quotaSnapshot,
            onOpenSubscription = { onNavigate(AppDestination.SUBSCRIPTION) }
        )

        Text("Core Language Studio", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickModuleTile(
                title = "AI Conversation",
                subtitle = if (isOnline) "Voice & roleplay" else "Requires internet",
                icon = Icons.Default.Forum,
                tag = "quick_ai_conversation",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(AppDestination.AI_CONVERSATION) }
            )
            QuickModuleTile(
                title = "Offline Practice",
                subtitle = "Lessons & quizzes",
                icon = Icons.Default.School,
                tag = "quick_practice",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(AppDestination.PRACTICE) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickModuleTile(
                title = "Dictionary",
                subtitle = "Verified IPA & audio",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                tag = "quick_dictionary",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(AppDestination.DICTIONARY) }
            )
            QuickModuleTile(
                title = "Translate & Speak",
                subtitle = "Tone & coaching",
                icon = Icons.Default.GTranslate,
                tag = "quick_translation",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(AppDestination.TRANSLATION) }
            )
        }

        if (unreadNotifs.isNotEmpty()) {
            FuturisticCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Notifications (${unreadNotifs.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = onMarkNotificationsRead) {
                        Text("Mark Read")
                    }
                }
                unreadNotifs.take(3).forEach { n ->
                    Spacer(Modifier.height(6.dp))
                    Text("${n.title}: ${n.body}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val activeSurvey = publishedSurveys.firstOrNull()
        if (activeSurvey != null) {
            var chosenOption by rememberSaveable(activeSurvey.id) { mutableStateOf("") }
            var freeComment by rememberSaveable(activeSurvey.id) { mutableStateOf("") }
            val opts = remember(activeSurvey.mcOptionsPipe) {
                activeSurvey.mcOptionsPipe.split("|").filter { it.isNotBlank() }
            }
            FuturisticCard {
                Text("Learner Pulse Survey: ${activeSurvey.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(activeSurvey.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(activeSurvey.mcQuestion, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opts.forEach { opt ->
                        FilterChip(
                            selected = chosenOption == opt,
                            onClick = { chosenOption = opt },
                            label = { Text(opt) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = freeComment,
                    onValueChange = { freeComment = it },
                    label = { Text(activeSurvey.freeTextQuestion) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSubmitSurvey(activeSurvey.id, chosenOption, freeComment) },
                    enabled = chosenOption.isNotBlank()
                ) {
                    Text("Submit Survey Response")
                }
            }
        }
    }
}

@Composable
private fun QuickModuleTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FuturisticCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConversationScreen(
    activeSession: ConversationSessionEntity?,
    messages: List<ConversationMessageEntity>,
    quotaSnapshot: QuotaStatusSnapshot?,
    isOnline: Boolean,
    isLocalModelAvailable: Boolean,
    isBusy: Boolean,
    isListening: Boolean,
    defaultLangCode: String,
    supportedLangsCsv: String,
    onStartSession: (lang: String, accent: String, scenarioId: String, difficulty: String) -> Unit,
    onSendMessage: (text: String, explainInNative: Boolean, autoSpeak: Boolean) -> Unit,
    onSpeakText: (text: String, accentTag: String) -> Unit,
    onStartVoiceInput: (accentTag: String, onRecognized: (String) -> Unit) -> Unit,
    onStopVoiceInput: () -> Unit,
    onFinishSession: () -> Unit
) {
    val context = LocalContext.current
    var selectedLang by rememberSaveable { mutableStateOf(defaultLangCode) }
    val langInfo = LanguageCatalog.byCode(selectedLang)
    var selectedAccent by rememberSaveable(selectedLang) {
        mutableStateOf(langInfo.accents.firstOrNull()?.bcp47Tag ?: langInfo.bcp47Tag)
    }
    var selectedDifficulty by rememberSaveable { mutableStateOf("Intermediate") }
    var selectedScenario by rememberSaveable { mutableStateOf("free_talk") }
    var explainInNative by rememberSaveable { mutableStateOf(true) }
    var autoSpeakReplies by rememberSaveable { mutableStateOf(false) }
    var draftText by rememberSaveable { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val tag = activeSession?.accentTag ?: selectedAccent
            onStartVoiceInput(tag) { recognized -> draftText = recognized }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Interna AI Conversation",
            subtitle = "Candid, adaptive dialogue with live grammar coaching & voice synthesis",
            icon = Icons.AutoMirrored.Filled.Chat
        )

        if (!isOnline && !isLocalModelAvailable) {
            FuturisticCard(accentBorder = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = AmberWarning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.disclaimer_offline_ai),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (activeSession == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FuturisticCard {
                    Text("1. Practice Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LanguageSelectorChips(
                        selectedCode = selectedLang,
                        onSelect = { selectedLang = it },
                        supportedCsv = supportedLangsCsv
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("2. Regional Accent / Variety", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        langInfo.accents.forEach { acc ->
                            FilterChip(
                                selected = selectedAccent == acc.bcp47Tag,
                                onClick = { selectedAccent = acc.bcp47Tag },
                                label = { Text(acc.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("3. Difficulty Level", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageCatalog.proficiencyLevels.forEach { lvl ->
                            FilterChip(
                                selected = selectedDifficulty == lvl,
                                onClick = { selectedDifficulty = lvl },
                                label = { Text(lvl) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("4. Roleplay Scenario", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    LanguageCatalog.roleplayScenarios.forEach { sc ->
                        val selected = selectedScenario == sc.id
                        FilterChip(
                            selected = selected,
                            onClick = { selectedScenario = sc.id },
                            label = {
                                Text(if (sc.isPremiumRecommended) "${sc.title} (Premium)" else sc.title)
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onStartSession(selectedLang, selectedAccent, selectedScenario, selectedDifficulty)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_conversation_button")
                    ) {
                        Text("Start Conversation with Interna AI")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val quotaLabel = if (quotaSnapshot?.isPremiumActive == true) {
                    "${(quotaSnapshot.remainingPremiumSeconds) / 60}m left today"
                } else {
                    "${quotaSnapshot?.remainingFreeTurns ?: 0} turns left today"
                }
                StatusPillBadge(
                    label = "${LanguageCatalog.byCode(activeSession.languageCode).englishName} • $quotaLabel",
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = onFinishSession,
                    modifier = Modifier.testTag("finish_conversation_button")
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("End & Summarize")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Native explanations", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = explainInNative, onCheckedChange = { explainInNative = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-speak", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = autoSpeakReplies, onCheckedChange = { autoSpeakReplies = it })
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.sender == "USER"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        FuturisticCard(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            accentBorder = !isUser
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUser) "You" else "Interna AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!isUser) {
                                    IconButton(
                                        onClick = { onSpeakText(msg.content, activeSession.accentTag) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = stringResource(R.string.cd_play_audio)
                                        )
                                    }
                                }
                            }
                            Text(msg.content, style = MaterialTheme.typography.bodyLarge)

                            if (msg.grammarCorrection.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "✓ Correction: ${msg.grammarCorrection}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldSuccess
                                )
                            }
                            if (msg.naturalAlternative.isNotBlank()) {
                                Text(
                                    "💡 Natural phrasing: ${msg.naturalAlternative}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (msg.vocabularySuggestions.isNotBlank()) {
                                Text(
                                    "📚 Vocabulary: ${msg.vocabularySuggestions}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (msg.nativeExplanation.isNotBlank()) {
                                Text(
                                    "ℹ️ Note: ${msg.nativeExplanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (isListening) {
                            onStopVoiceInput()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onStartVoiceInput(activeSession.accentTag) { draftText = it }
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("conversation_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.cd_voice_input),
                        tint = if (isListening) AmberWarning else MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    placeholder = { Text("Message Interna AI...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("conversation_message_input"),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        if (draftText.isNotBlank() && !isBusy) {
                            val toSend = draftText
                            draftText = ""
                            onSendMessage(toSend, explainInNative, autoSpeakReplies)
                        }
                    },
                    enabled = draftText.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("conversation_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_message),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationSummaryScreen(
    report: SessionSummaryReport?,
    targetLanguageCode: String,
    onSaveVocab: (term: String, meaning: String, example: String) -> Unit,
    onDone: () -> Unit
) {
    BackHandler(onBack = onDone)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Session Summary",
            subtitle = "Key takeaways, grammar refinements & saveable vocabulary",
            icon = Icons.Default.RecordVoiceOver
        )

        if (report == null) {
            FuturisticCard { Text("No session report available.") }
        } else {
            FuturisticCard(accentBorder = true) {
                StatusPillBadge(label = "SESSION SCORE: ${report.score}/100", color = EmeraldSuccess)
                Spacer(Modifier.height(8.dp))
                Text(report.summary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Next Step: ${report.nextStepAdvice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (report.corrections.isNotEmpty()) {
                FuturisticCard {
                    Text("Key Corrections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    report.corrections.forEach { c ->
                        Text("• $c", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (report.newVocabulary.isNotEmpty()) {
                FuturisticCard {
                    Text("New Vocabulary (${LanguageCatalog.byCode(targetLanguageCode).englishName})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    report.newVocabulary.forEach { vp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${vp.term} — ${vp.meaning}", fontWeight = FontWeight.SemiBold)
                                if (vp.example.isNotBlank()) {
                                    Text(vp.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onSaveVocab(vp.term, vp.meaning, vp.example) }) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Save to flashcards")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Return to Home Dashboard")
        }
    }
}
