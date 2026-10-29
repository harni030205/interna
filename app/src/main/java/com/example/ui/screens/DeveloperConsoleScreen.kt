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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminAuditLogEntity
import com.example.data.local.ApplicationSettingsEntity
import com.example.data.local.FeedbackEntity
import com.example.data.local.PaymentMethodConfigEntity
import com.example.data.local.SubscriptionRequestEntity
import com.example.data.local.SurveyEntity
import com.example.data.local.SurveyResponseEntity
import com.example.data.repository.AdminOverviewMetrics
import com.example.data.repository.AdminUserSummaryRow
import com.example.ui.DeveloperSubTab
import com.example.ui.components.FuturisticCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun DeveloperConsoleScreen(
    isVerifiedAdmin: Boolean,
    activeSubTab: DeveloperSubTab,
    overview: AdminOverviewMetrics?,
    usersList: List<AdminUserSummaryRow>,
    paymentConfigs: List<PaymentMethodConfigEntity>,
    subscriptionRequests: List<SubscriptionRequestEntity>,
    surveys: List<SurveyEntity>,
    surveyResponses: List<SurveyResponseEntity>,
    feedbackItems: List<FeedbackEntity>,
    appSettings: ApplicationSettingsEntity,
    auditLogs: List<AdminAuditLogEntity>,
    onSelectSubTab: (DeveloperSubTab) -> Unit,
    onSearchUsers: (query: String, statusFilter: String, page: Int) -> Unit,
    onSavePaymentConfig: (
        methodCode: String,
        isEnabled: Boolean,
        accountNumber: String,
        accountName: String,
        currency: String,
        monthlyPrice: String,
        quarterlyPrice: String,
        yearlyPrice: String,
        instructions: String
    ) -> Unit,
    onApproveSubscription: (requestId: String, days: Int, dailyMinutes: Int) -> Unit,
    onRejectSubscription: (requestId: String, reason: String) -> Unit,
    onToggleUserSuspension: (userId: String, suspend: Boolean) -> Unit,
    onChangeUserPlan: (userId: String, grantPremium: Boolean, days: Int) -> Unit,
    onResetUserQuota: (userId: String) -> Unit,
    onUpdateUserLimitAndNote: (userId: String, customLimit: Int?, note: String) -> Unit,
    onSaveGlobalControls: (ApplicationSettingsEntity) -> Unit,
    onCreateSurvey: (title: String, desc: String, mcQ: String, opts: List<String>, freeQ: String) -> Unit,
    onToggleSurveyStatus: (SurveyEntity, Boolean) -> Unit,
    onUpdateFeedbackStatus: (feedbackId: String, newStatus: String) -> Unit,
    onExportMinimizedReport: () -> Unit
) {
    if (!isVerifiedAdmin) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Restricted", tint = RoseError)
            Spacer(Modifier.height(12.dp))
            Text("403 Unauthorized", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Developer Console requires a cryptographically verified ADMIN role grant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val tabLabels = listOf(
        DeveloperSubTab.OVERVIEW to "Overview",
        DeveloperSubTab.USERS to "Users & Subs",
        DeveloperSubTab.SUBSCRIPTIONS to "Payments & Approvals",
        DeveloperSubTab.SURVEYS_FEEDBACK to "Surveys & Support",
        DeveloperSubTab.USAGE_CONTROLS to "App Settings",
        DeveloperSubTab.AUDIT_LOG to "Audit Log"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Developer Console",
            subtitle = "Users, subscriptions, mobile-money payment verification, surveys, support & app settings",
            icon = Icons.Default.Shield
        )

        ScrollableTabRow(
            selectedTabIndex = DeveloperSubTab.entries.indexOf(activeSubTab),
            edgePadding = 0.dp
        ) {
            tabLabels.forEach { (tab, label) ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { onSelectSubTab(tab) },
                    text = { Text(label) },
                    modifier = Modifier.testTag("admin_tab_${tab.name}")
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (activeSubTab) {
            DeveloperSubTab.OVERVIEW -> AdminOverviewTab(
                overview = overview,
                onExportReport = onExportMinimizedReport
            )
            DeveloperSubTab.USERS -> AdminUsersManagementTab(
                usersList = usersList,
                onSearchUsers = onSearchUsers,
                onToggleSuspension = onToggleUserSuspension,
                onChangePlan = onChangeUserPlan,
                onResetQuota = onResetUserQuota,
                onUpdateLimitAndNote = onUpdateUserLimitAndNote
            )
            DeveloperSubTab.SUBSCRIPTIONS -> AdminPaymentsAndApprovalsTab(
                paymentConfigs = paymentConfigs,
                requests = subscriptionRequests,
                defaultDailyMins = appSettings.premiumOnlineDailyMinutesLimit,
                onSavePaymentConfig = onSavePaymentConfig,
                onApprove = onApproveSubscription,
                onReject = onRejectSubscription
            )
            DeveloperSubTab.SURVEYS_FEEDBACK -> AdminSurveysAndFeedbackTab(
                surveys = surveys,
                responses = surveyResponses,
                feedbackItems = feedbackItems,
                onCreateSurvey = onCreateSurvey,
                onToggleSurveyStatus = onToggleSurveyStatus,
                onUpdateFeedbackStatus = onUpdateFeedbackStatus
            )
            DeveloperSubTab.USAGE_CONTROLS -> AdminUsageControlsTab(
                currentSettings = appSettings,
                onSaveControls = onSaveGlobalControls
            )
            DeveloperSubTab.AUDIT_LOG -> AdminAuditLogTab(
                auditLogs = auditLogs
            )
        }
    }
}

@Composable
private fun AdminOverviewTab(
    overview: AdminOverviewMetrics?,
    onExportReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (overview == null) {
            FuturisticCard { Text("Loading real-time platform metrics...") }
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FuturisticCard(modifier = Modifier.weight(1f), accentBorder = true) {
                Text("${overview.totalRegisteredUsers}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Total Registered Users", style = MaterialTheme.typography.bodySmall)
                Text("+${overview.newUsersLast7Days} last 7d", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
            }
            FuturisticCard(modifier = Modifier.weight(1f)) {
                Text("${overview.activePremiumSubscriptions}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Active Premium Plans", style = MaterialTheme.typography.bodySmall)
                Text("${overview.pendingSubscriptionRequests} pending verification", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
            }
        }

        FuturisticCard {
            Text("Engagement & Payment Verification Funnel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Active Learners: DAU ${overview.dailyActiveUsers} • WAU ${overview.weeklyActiveUsers} • MAU ${overview.monthlyActiveUsers}")
            Text("Payment Requests: ${overview.pendingSubscriptionRequests} Pending • ${overview.approvedSubscriptionRequests} Approved • ${overview.rejectedSubscriptionRequests} Rejected")
        }

        FuturisticCard {
            Text("AI Infrastructure & Quota Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Total AI Requests Served: ${overview.totalAiConversationRequests}")
            Text("Accounts Near Daily Limit Today (>=80%): ${overview.usageLimitWarningsToday}")
            Text("Recorded API Error Count: ${overview.totalApiErrors}")
            Text("Estimated Cumulative Tokens: ${overview.estimatedTotalTokens}")
            Spacer(Modifier.height(6.dp))
            Text(
                text = overview.billingIntegrationStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onExportReport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_export_report_button")
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Export Minimized Aggregate Report (JSON)")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminSurveysAndFeedbackTab(
    surveys: List<SurveyEntity>,
    responses: List<SurveyResponseEntity>,
    feedbackItems: List<FeedbackEntity>,
    onCreateSurvey: (title: String, desc: String, mcQ: String, opts: List<String>, freeQ: String) -> Unit,
    onToggleSurveyStatus: (SurveyEntity, Boolean) -> Unit,
    onUpdateFeedbackStatus: (feedbackId: String, newStatus: String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var mcQuestion by rememberSaveable { mutableStateOf("") }
    var mcOptionsCsv by rememberSaveable { mutableStateOf("AI Conversation,Dictionary,Pronunciation,Offline Quizzes") }
    var freeQ by rememberSaveable { mutableStateOf("What language or feature should we improve next?") }
    var feedbackFilter by rememberSaveable { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FuturisticCard {
            Text("Create & Publish Learner Survey", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Survey Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = mcQuestion, onValueChange = { mcQuestion = it }, label = { Text("Multiple-Choice Question") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = mcOptionsCsv, onValueChange = { mcOptionsCsv = it }, label = { Text("Options (comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = freeQ, onValueChange = { freeQ = it }, label = { Text("Free-Text Prompt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val opts = mcOptionsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onCreateSurvey(title, desc, mcQuestion, opts, freeQ)
                    title = ""
                    desc = ""
                    mcQuestion = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Publish Survey")
            }
        }

        FuturisticCard {
            Text("Active & Past Surveys (${surveys.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            surveys.forEach { survey ->
                val surveyResps = responses.filter { it.surveyId == survey.id }
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(survey.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { onToggleSurveyStatus(survey, !survey.isPublished) }) {
                            Text(if (survey.isPublished) "Close" else "Publish")
                        }
                    }
                    Text("Responses: ${surveyResps.size} • Status: ${if (survey.isPublished) "Published" else "Closed"}", style = MaterialTheme.typography.bodySmall)
                    val counts = surveyResps.groupingBy { it.selectedMcOption }.eachCount()
                    counts.forEach { (opt, c) ->
                        Text("  • $opt: $c vote(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        FuturisticCard {
            Text("Support Messages & Learner Reports (${feedbackItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "BUG_REPORT", "FEATURE_SUGGESTION", "CONTENT_ISSUE", "GENERAL").forEach { cat ->
                    FilterChip(
                        selected = feedbackFilter == cat,
                        onClick = { feedbackFilter = cat },
                        label = { Text(cat.replace("_", " ")) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val shown = if (feedbackFilter == "ALL") feedbackItems else feedbackItems.filter { it.category == feedbackFilter }
            if (shown.isEmpty()) {
                Text("No support messages in this category.", style = MaterialTheme.typography.bodySmall)
            } else {
                shown.forEach { item ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.subject} (${item.category})", fontWeight = FontWeight.Bold)
                            StatusPillBadge(label = item.status, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("From: ${item.userEmail}", style = MaterialTheme.typography.labelSmall)
                        Text(item.message, style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("OPEN", "REVIEWED", "RESOLVED").forEach { st ->
                                FilterChip(
                                    selected = item.status == st,
                                    onClick = { onUpdateFeedbackStatus(item.id, st) },
                                    label = { Text(st) }
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
