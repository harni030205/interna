package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PaymentMethodConfigEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.SubscriptionRequestEntity
import com.example.data.repository.QuotaStatusSnapshot
import com.example.domain.model.MobileMoneyMethod
import com.example.domain.model.PremiumPlanOption
import com.example.domain.model.isConfiguredForPayments
import com.example.ui.components.FuturisticCard
import com.example.ui.components.QuotaSummaryMeterCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionScreen(
    quotaSnapshot: QuotaStatusSnapshot?,
    subscription: SubscriptionEntity?,
    paymentConfigs: List<PaymentMethodConfigEntity>,
    requests: List<SubscriptionRequestEntity>,
    onSubmitPaymentRequest: (
        requestedPlan: String,
        paymentMethod: String,
        transactionReference: String,
        amountPaid: String,
        paymentDate: String,
        proofOfPaymentUri: String,
        optionalMessage: String
    ) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val todayUtc = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    var selectedPlan by rememberSaveable { mutableStateOf(PremiumPlanOption.MONTHLY.planCode) }
    var selectedMethod by rememberSaveable { mutableStateOf(MobileMoneyMethod.YAS_MONEY.code) }
    var txReference by rememberSaveable { mutableStateOf("") }
    var amountPaid by rememberSaveable { mutableStateOf("") }
    var paymentDate by rememberSaveable { mutableStateOf(todayUtc) }
    var proofUri by rememberSaveable { mutableStateOf("") }
    var optionalNote by rememberSaveable { mutableStateOf("") }

    val planEnum = PremiumPlanOption.fromPlanCode(selectedPlan) ?: PremiumPlanOption.MONTHLY
    val methodConfig = paymentConfigs.find { it.methodCode == selectedMethod }
    val configuredPrice = planEnum.configuredPrice(methodConfig)
    val isMethodReady = methodConfig?.isConfiguredForPayments() == true && configuredPrice.isNotBlank()

    LaunchedEffect(selectedPlan, selectedMethod, configuredPrice) {
        if (configuredPrice.isNotBlank()) {
            amountPaid = configuredPrice
        }
    }

    val proofPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            proofUri = uri.toString()
        }
    }

    val hasPending = requests.any { it.status == "PENDING" }
    val isApprovedActive = quotaSnapshot?.isPremiumActive == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Subscription & Mobile-Money Payments",
            subtitle = "Choose a Premium plan, transfer via Yas Money, Airtel Money, or Orange Money, and submit for verification",
            icon = Icons.Default.WorkspacePremium
        )

        QuotaSummaryMeterCard(
            snapshot = quotaSnapshot,
            onOpenSubscription = {}
        )

        // Current Subscription & Payment Status Card
        FuturisticCard(accentBorder = isApprovedActive || hasPending) {
            val statusLabel = quotaSnapshot?.subscriptionStatus ?: subscription?.status ?: "NONE"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subscription & Payment Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusPillBadge(
                    label = statusLabel,
                    color = when (statusLabel) {
                        "APPROVED" -> EmeraldSuccess
                        "PENDING" -> AmberWarning
                        "REJECTED", "EXPIRED", "SUSPENDED" -> RoseError
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            if (subscription != null && subscription.status != "NONE") {
                val durationDays = if (subscription.startDate > 0L && subscription.expirationDate > subscription.startDate) {
                    ((subscription.expirationDate - subscription.startDate) / 86_400_000L).toInt().coerceAtLeast(1)
                } else {
                    quotaSnapshot?.subscriptionDurationDays ?: 0
                }
                Text("Plan: ${PremiumPlanOption.displayTitleFor(subscription.planType)}")
                if (durationDays > 0) {
                    Text("Approved Duration: $durationDays day(s) • Daily Allowance: ${subscription.dailyMinutesAllowance} mins/day")
                }
                if (subscription.expirationDate > 0L) {
                    Text(
                        text = "Expiration Date: ${dateFormat.format(Date(subscription.expirationDate))}",
                        fontWeight = FontWeight.SemiBold,
                        color = if (isApprovedActive) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (hasPending) {
                Text(
                    text = "Your mobile-money payment submission is Pending manual administrator verification. Premium features activate immediately once verified and approved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberWarning
                )
            } else {
                Text(
                    text = "Currently on the Free Plan (up to ${quotaSnapshot?.freeTurnsLimit ?: 20} conversation turns/day + full offline lessons & dictionary cache).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Step 1 & 2: Choose Plan & Mobile-Money Method
        FuturisticCard(accentBorder = true) {
            Text("1. Choose Premium Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Unlocks 120 minutes/day of online Interna AI voice & text conversation, contextual translation, nuanced dictionary explanations, and all study packs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPlanOption.entries.forEach { option ->
                    FilterChip(
                        selected = selectedPlan == option.planCode,
                        onClick = { selectedPlan = option.planCode },
                        label = { Text(option.title) },
                        modifier = Modifier.testTag("plan_chip_${option.planCode}")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("2. Choose Mobile-Money Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileMoneyMethod.entries.forEach { method ->
                    FilterChip(
                        selected = selectedMethod == method.code,
                        onClick = { selectedMethod = method.code },
                        label = { Text(method.displayName) },
                        modifier = Modifier.testTag("payment_method_chip_${method.code}")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Developer-configured Receiving Details (Never invented!)
            if (isMethodReady && methodConfig != null) {
                Text(
                    text = "${methodConfig.displayName} Transfer Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Receiving Number: ${methodConfig.receivingAccountNumber}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Account Name: ${methodConfig.receivingAccountName}")
                        Text(
                            text = "Plan Price: $configuredPrice ${methodConfig.currency} (${planEnum.durationDays} days)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Receiving Number", methodConfig.receivingAccountNumber)
                            )
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy number")
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
                if (methodConfig.transferInstructions.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Instructions: ${methodConfig.transferInstructions}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                StatusPillBadge(
                    label = "AWAITING ADMINISTRATOR PAYMENT CONFIGURATION",
                    color = AmberWarning
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${MobileMoneyMethod.displayLabel(selectedMethod)} receiving account details or pricing for ${planEnum.title} have not been configured by the administrator yet. Please check another payment method or try again shortly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Step 3: Submit Transfer Verification Form
        FuturisticCard {
            Text(
                text = "3. Submit Mobile-Money Payment Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Manual Verification Notice: Submitting a transaction reference or attaching a receipt records your request as Pending. Premium access activates only after the administrator verifies the transfer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = txReference,
                onValueChange = { txReference = it },
                label = { Text("Transaction Reference / SMS Receipt ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_tx_ref_input")
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amountPaid,
                    onValueChange = { amountPaid = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount Paid (${methodConfig?.currency ?: "Currency"})") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_amount_input")
                )
                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_date_input")
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        proofPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("attach_payment_proof_button")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (proofUri.isBlank()) "Attach Proof (Optional)" else "Change Proof Image")
                }
                if (proofUri.isNotBlank()) {
                    Text(
                        text = "Proof attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = optionalNote,
                onValueChange = { optionalNote = it },
                label = { Text("Optional Sender Name / Note") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subscription_note_input")
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSubmitPaymentRequest(
                        selectedPlan,
                        selectedMethod,
                        txReference,
                        amountPaid,
                        paymentDate,
                        proofUri,
                        optionalNote
                    )
                    txReference = ""
                    proofUri = ""
                    optionalNote = ""
                },
                enabled = isMethodReady && !hasPending && !isApprovedActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_subscription_request_button")
            ) {
                Text(
                    when {
                        isApprovedActive -> "Premium Plan Currently Active"
                        hasPending -> "Payment Pending Administrator Verification"
                        !isMethodReady -> "Select a Configured Payment Method"
                        else -> "Submit Payment for Verification"
                    }
                )
            }
        }

        // Request History
        FuturisticCard {
            Text("Payment & Subscription Request History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (requests.isEmpty()) {
                Text(
                    "No payment requests submitted yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                requests.forEach { req ->
                    val badgeColor = when (req.status) {
                        "APPROVED" -> EmeraldSuccess
                        "PENDING" -> AmberWarning
                        "REJECTED", "SUSPENDED", "EXPIRED" -> RoseError
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${PremiumPlanOption.displayTitleFor(req.requestedPlan)} • ${MobileMoneyMethod.displayLabel(req.paymentMethod)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: ${req.id} • ${dateFormat.format(Date(req.createdAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPillBadge(label = req.status, color = badgeColor)
                        }
                        if (req.transactionReference.isNotBlank()) {
                            Text(
                                text = "Ref: ${req.transactionReference} • Amount: ${req.amountPaid} ${req.currency} • Date: ${req.paymentDate}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "Duration: ${req.durationDays} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (req.optionalMessage.isNotBlank()) {
                            Text("Your note: ${req.optionalMessage}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (req.status == "REJECTED" && req.rejectionReason.isNotBlank()) {
                            Text(
                                text = "Verification note: ${req.rejectionReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoseError
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
