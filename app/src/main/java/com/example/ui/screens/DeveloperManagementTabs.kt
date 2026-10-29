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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminAuditLogEntity
import com.example.data.local.ApplicationSettingsEntity
import com.example.data.repository.AdminUserSummaryRow
import com.example.ui.components.DestructiveActionConfirmDialog
import com.example.ui.components.FuturisticCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminUsersManagementTab(
    usersList: List<AdminUserSummaryRow>,
    onSearchUsers: (query: String, statusFilter: String, page: Int) -> Unit,
    onToggleSuspension: (userId: String, suspend: Boolean) -> Unit,
    onChangePlan: (userId: String, grantPremium: Boolean, days: Int) -> Unit,
    onResetQuota: (userId: String) -> Unit,
    onUpdateLimitAndNote: (userId: String, customLimit: Int?, note: String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var pendingSuspendTarget by rememberSaveable { mutableStateOf<Pair<String, Boolean>?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FuturisticCard {
            Text(
                text = "Privacy Boundary: User management exposes account metadata, plan status, and aggregate usage counters only. Private AI conversation transcripts are never displayed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    page = 0
                    onSearchUsers(it, statusFilter, 0)
                },
                label = { Text("Search by Name or Email") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search_input")
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "ACTIVE", "SUSPENDED").forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = {
                            statusFilter = st
                            page = 0
                            onSearchUsers(query, st, 0)
                        },
                        label = { Text(st) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (page > 0) {
                            page -= 1
                            onSearchUsers(query, statusFilter, page)
                        }
                    },
                    enabled = page > 0
                ) {
                    Text("Previous")
                }
                Text("Page ${page + 1}", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = {
                        page += 1
                        onSearchUsers(query, statusFilter, page)
                    },
                    enabled = usersList.size >= 15
                ) {
                    Text("Next")
                }
            }
        }

        usersList.forEach { row ->
            val u = row.user
            val sub = row.subscription
            var customLimitText by rememberSaveable(u.id) {
                mutableStateOf(u.customDailyAllowanceOverride?.toString().orEmpty())
            }
            var adminNoteText by rememberSaveable(u.id) { mutableStateOf(u.adminInternalNote) }

            FuturisticCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${u.fullName} (${u.email})", fontWeight = FontWeight.Bold)
                        Text(
                            "Joined ${dateFormat.format(Date(u.createdAt))} • Native: ${u.nativeLanguage} • Target: ${u.targetLanguagesCsv} (${u.proficiencyLevel})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    StatusPillBadge(
                        label = u.accountStatus,
                        color = if (u.accountStatus == "ACTIVE") EmeraldSuccess else RoseError
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Plan: ${sub?.planType ?: "FREE_OFFLINE"} (${sub?.status ?: "NONE"}) • Turns: ${row.totalTurnsUsed} • Premium Mins: ${row.totalPremiumSecondsUsed / 60L} • XP: ${row.totalXpEarned}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customLimitText,
                        onValueChange = { customLimitText = it.filter { c -> c.isDigit() } },
                        label = { Text("Custom Limit Override") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = adminNoteText,
                        onValueChange = { adminNoteText = it },
                        label = { Text("Internal Admin Note") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onUpdateLimitAndNote(u.id, customLimitText.toIntOrNull(), adminNoteText)
                        }
                    ) {
                        Text("Save Limit & Note")
                    }
                    OutlinedButton(onClick = { onResetQuota(u.id) }) {
                        Text("Reset Daily Quota")
                    }
                    val hasActivePremium = sub?.planType == "PREMIUM_ONLINE" && sub.status == "APPROVED"
                    OutlinedButton(
                        onClick = { onChangePlan(u.id, !hasActivePremium, 30) }
                    ) {
                        Text(if (hasActivePremium) "Revoke to Free" else "Grant Premium (30d)")
                    }
                    val willSuspend = u.accountStatus == "ACTIVE"
                    Button(
                        onClick = { pendingSuspendTarget = u.id to willSuspend },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (willSuspend) MaterialTheme.colorScheme.error else EmeraldSuccess
                        )
                    ) {
                        Text(if (willSuspend) "Suspend" else "Restore")
                    }
                }
            }
        }
    }

    pendingSuspendTarget?.let { (targetId, suspend) ->
        DestructiveActionConfirmDialog(
            title = if (suspend) "Suspend User Account?" else "Restore User Account?",
            body = if (suspend) {
                "Suspended users cannot sign in or invoke AI endpoints until restored. An immutable audit log entry will be recorded."
            } else {
                "Restore sign-in and study access for this account?"
            },
            confirmText = if (suspend) "Suspend Account" else "Restore Account",
            onConfirm = {
                onToggleSuspension(targetId, suspend)
                pendingSuspendTarget = null
            },
            onDismiss = { pendingSuspendTarget = null }
        )
    }
}

@Composable
fun AdminUsageControlsTab(
    currentSettings: ApplicationSettingsEntity,
    onSaveControls: (ApplicationSettingsEntity) -> Unit
) {
    var freeTurns by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.freeOfflineDailyTurnsLimit.toString())
    }
    var premiumMins by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.premiumOnlineDailyMinutesLimit.toString())
    }
    var supportedCsv by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.supportedLanguagesCsv)
    }
    var maxSessionTurns by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.maxConversationTurnsPerSession.toString())
    }
    var defaultSubDays by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.defaultSubscriptionDurationDays.toString())
    }
    var rateLimitRpm by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.rateLimitRequestsPerMinute.toString())
    }
    var aiChatEnabled by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.aiConversationEnabled)
    }
    var dictAiEnabled by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.dictionaryAiEnabled)
    }
    var pronEnabled by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.pronunciationCoachEnabled)
    }
    var maintenanceMode by rememberSaveable(currentSettings.updatedAt) {
        mutableStateOf(currentSettings.maintenanceMode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FuturisticCard(accentBorder = true) {
            Text("Global Quotas & Service Switches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = freeTurns,
                onValueChange = { freeTurns = it.filter { c -> c.isDigit() } },
                label = { Text("Free Plan Daily Turns Limit (default 20)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = premiumMins,
                onValueChange = { premiumMins = it.filter { c -> c.isDigit() } },
                label = { Text("Premium Online Daily Minutes Limit (default 120)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = supportedCsv,
                onValueChange = { supportedCsv = it },
                label = { Text("Supported Languages CSV") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = maxSessionTurns,
                    onValueChange = { maxSessionTurns = it.filter { c -> c.isDigit() } },
                    label = { Text("Max Session Turns") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = defaultSubDays,
                    onValueChange = { defaultSubDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Default Sub Days") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rateLimitRpm,
                    onValueChange = { rateLimitRpm = it.filter { c -> c.isDigit() } },
                    label = { Text("Rate Limit/min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            ToggleRow("Enable AI Conversation Service", aiChatEnabled) { aiChatEnabled = it }
            ToggleRow("Enable Dictionary AI Explanations", dictAiEnabled) { dictAiEnabled = it }
            ToggleRow("Enable Pronunciation Coach", pronEnabled) { pronEnabled = it }
            ToggleRow("Maintenance Mode (Blocks non-essential AI)", maintenanceMode) { maintenanceMode = it }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSaveControls(
                        currentSettings.copy(
                            freeOfflineDailyTurnsLimit = freeTurns.toIntOrNull() ?: 20,
                            premiumOnlineDailyMinutesLimit = premiumMins.toIntOrNull() ?: 120,
                            supportedLanguagesCsv = supportedCsv,
                            maxConversationTurnsPerSession = maxSessionTurns.toIntOrNull() ?: 50,
                            defaultSubscriptionDurationDays = defaultSubDays.toIntOrNull() ?: 30,
                            rateLimitRequestsPerMinute = rateLimitRpm.toIntOrNull() ?: 15,
                            aiConversationEnabled = aiChatEnabled,
                            dictionaryAiEnabled = dictAiEnabled,
                            pronunciationCoachEnabled = pronEnabled,
                            maintenanceMode = maintenanceMode
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_global_controls_button")
            ) {
                Text("Save & Enforce Global Usage Controls")
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AdminAuditLogTab(
    auditLogs: List<AdminAuditLogEntity>
) {
    var filterQuery by rememberSaveable { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val filtered = remember(auditLogs, filterQuery) {
        if (filterQuery.isBlank()) auditLogs
        else auditLogs.filter {
            it.actionType.contains(filterQuery, ignoreCase = true) ||
                it.affectedTarget.contains(filterQuery, ignoreCase = true) ||
                it.sanitizedSummary.contains(filterQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FuturisticCard {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                label = { Text("Filter Audit Trail (action, target, summary)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filtered.isEmpty()) {
            FuturisticCard { Text("No administrative audit events recorded yet.") }
        } else {
            FuturisticCard {
                filtered.forEach { log ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(log.actionType, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            StatusPillBadge(
                                label = if (log.succeeded) "SUCCESS" else "BLOCKED",
                                color = if (log.succeeded) EmeraldSuccess else AmberWarning
                            )
                        }
                        Text(
                            text = "${dateFormat.format(Date(log.timestamp))} • Admin: ${log.adminEmail} • Target: ${log.affectedTarget}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(log.sanitizedSummary, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
