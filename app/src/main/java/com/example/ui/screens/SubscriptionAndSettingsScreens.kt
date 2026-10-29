package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.UserEntity
import com.example.data.local.UserPreferenceEntity
import com.example.domain.model.LanguageCatalog
import com.example.security.CryptoSecurityManager
import com.example.ui.components.FuturisticCard
import com.example.ui.components.LanguageSelectorChips
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.i18n.InterfaceLocalizer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    user: UserEntity?,
    preferences: UserPreferenceEntity?,
    dispatchedVerificationHint: String?,
    isVerifiedAdmin: Boolean,
    onUpdateProfile: (
        fullName: String,
        nativeLang: String,
        targetLangsCsv: String,
        activeTargetLang: String,
        level: String,
        goal: String,
        dailyMinutes: Int
    ) -> Unit,
    onUpdatePreferences: (
        interfaceLanguage: String?,
        themeMode: String?,
        saveHistory: Boolean?,
        nativeExplanations: Boolean?
    ) -> Unit,
    onVerifyEmail: (String) -> Unit,
    onResendVerificationCode: () -> Unit,
    onSubmitFeedback: (category: String, subject: String, message: String) -> Unit,
    onExportPersonalData: () -> Unit,
    onOpenDeveloperProvisioning: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: (currentPassword: String) -> Unit
) {
    var fullName by rememberSaveable(user?.id) { mutableStateOf(user?.fullName.orEmpty()) }
    var nativeLang by rememberSaveable(user?.id) { mutableStateOf(user?.nativeLanguage ?: "en") }
    var activeTargetLang by rememberSaveable(user?.id) { mutableStateOf(user?.activeTargetLanguage ?: "fr") }
    var targetCsv by rememberSaveable(user?.id) { mutableStateOf(user?.targetLanguagesCsv ?: "fr,es,ja") }
    var level by rememberSaveable(user?.id) { mutableStateOf(user?.proficiencyLevel ?: "Intermediate") }
    var goal by rememberSaveable(user?.id) { mutableStateOf(user?.learningGoal ?: "Conversational Fluency") }
    var dailyMinutesText by rememberSaveable(user?.id) { mutableStateOf((user?.dailyStudyMinutesGoal ?: 20).toString()) }

    var emailCode by rememberSaveable { mutableStateOf("") }
    var feedbackCategory by rememberSaveable { mutableStateOf("FEATURE_SUGGESTION") }
    var feedbackSubject by rememberSaveable { mutableStateOf("") }
    var feedbackMessage by rememberSaveable { mutableStateOf("") }

    var showDeleteModal by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmPassword by rememberSaveable { mutableStateOf("") }

    val isEligibleDeveloperEmail = user?.email?.equals(
        CryptoSecurityManager.INITIAL_AUTHORIZED_ADMIN_EMAIL,
        ignoreCase = true
    ) == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Settings & Privacy",
            subtitle = "Profile, interface language, theme, privacy controls & data portability",
            icon = Icons.Default.Settings
        )

        if (user != null) {
            FuturisticCard(accentBorder = !user.emailVerified) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (user.emailVerified) "Email address verified" else "Email verification pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPillBadge(
                        label = if (user.emailVerified) "VERIFIED" else "UNVERIFIED",
                        color = if (user.emailVerified) EmeraldSuccess else AmberWarning
                    )
                }

                if (!user.emailVerified) {
                    Spacer(Modifier.height(10.dp))
                    if (!dispatchedVerificationHint.isNullOrBlank()) {
                        Text(
                            text = "Verification code dispatch preview: $dispatchedVerificationHint",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedTextField(
                        value = emailCode,
                        onValueChange = { emailCode = it },
                        label = { Text("Enter 6-Digit Verification Code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_verify_code_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onVerifyEmail(emailCode) },
                            modifier = Modifier.testTag("settings_verify_code_button")
                        ) {
                            Text("Verify Email")
                        }
                        OutlinedButton(onClick = onResendVerificationCode) {
                            Text("Generate New Code")
                        }
                    }
                }
            }
        }

        FuturisticCard {
            Text("Interface Language & Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Interface Language", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InterfaceLocalizer.supportedInterfaceLanguages.forEach { (code, label) ->
                    FilterChip(
                        selected = (preferences?.interfaceLanguage ?: "en") == code,
                        onClick = { onUpdatePreferences(code, null, null, null) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DARK" to "Dark Navy", "LIGHT" to "Light Mode", "SYSTEM" to "System Auto").forEach { (mode, label) ->
                    FilterChip(
                        selected = (preferences?.themeMode ?: "DARK") == mode,
                        onClick = { onUpdatePreferences(null, mode, null, null) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("theme_chip_$mode")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Save AI Conversation History", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "When off, session messages stay ephemeral in memory only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences?.saveConversationHistory ?: true,
                    onCheckedChange = { onUpdatePreferences(null, null, it, null) }
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Native Language Explanations", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Include concise mistake explanations in your native language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences?.nativeExplanationsEnabled ?: true,
                    onCheckedChange = { onUpdatePreferences(null, null, null, it) }
                )
            }
        }

        FuturisticCard {
            Text("Learner Profile & Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Native Language", style = MaterialTheme.typography.labelMedium)
            LanguageSelectorChips(selectedCode = nativeLang, onSelect = { nativeLang = it })

            Spacer(Modifier.height(8.dp))
            Text("Active Target Language", style = MaterialTheme.typography.labelMedium)
            LanguageSelectorChips(selectedCode = activeTargetLang, onSelect = { activeTargetLang = it })

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = targetCsv,
                onValueChange = { targetCsv = it },
                label = { Text("Target Languages Codes (comma-separated, e.g. fr,es,ja)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Proficiency Level", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageCatalog.proficiencyLevels.forEach { lvl ->
                    FilterChip(selected = level == lvl, onClick = { level = lvl }, label = { Text(lvl) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dailyMinutesText,
                onValueChange = { dailyMinutesText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Daily Study Goal (minutes)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onUpdateProfile(
                        fullName,
                        nativeLang,
                        targetCsv,
                        activeTargetLang,
                        level,
                        goal,
                        dailyMinutesText.toIntOrNull() ?: 20
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_profile_button")
            ) {
                Text("Save Profile Changes")
            }
        }

        FuturisticCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Support Messages & Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("FEATURE_SUGGESTION", "BUG_REPORT", "CONTENT_ISSUE", "GENERAL").forEach { cat ->
                    FilterChip(
                        selected = feedbackCategory == cat,
                        onClick = { feedbackCategory = cat },
                        label = { Text(cat.replace("_", " ")) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = feedbackSubject,
                onValueChange = { feedbackSubject = it },
                label = { Text("Subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = feedbackMessage,
                onValueChange = { feedbackMessage = it },
                label = { Text("Details") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onSubmitFeedback(feedbackCategory, feedbackSubject, feedbackMessage)
                    feedbackSubject = ""
                    feedbackMessage = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Support Message")
            }
        }

        FuturisticCard {
            Text("Account & Data Governance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onExportPersonalData,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_personal_data_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export My Personal Data (JSON)")
            }

            // Never expose developer provisioning controls to ordinary user accounts
            if (isVerifiedAdmin || isEligibleDeveloperEmail) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenDeveloperProvisioning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_developer_auth_button")
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isVerifiedAdmin) "Developer Console Access (Verified)" else "Developer Role Provisioning")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showDeleteModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_account_button")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Account & Erase Personal Data")
            }
        }
    }

    if (showDeleteModal) {
        AlertDialog(
            onDismissRequest = { showDeleteModal = false },
            title = { Text("Permanently Delete Account?") },
            text = {
                Column {
                    Text(
                        "This permanently erases your profile, saved vocabulary, conversation history, progress records, and entitlements. Enter your password to confirm."
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deleteConfirmPassword,
                        onValueChange = { deleteConfirmPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteModal = false
                        onDeleteAccount(deleteConfirmPassword)
                        deleteConfirmPassword = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
