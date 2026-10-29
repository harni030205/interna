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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PaymentMethodConfigEntity
import com.example.data.local.SubscriptionRequestEntity
import com.example.domain.model.MobileMoneyMethod
import com.example.domain.model.PremiumPlanOption
import com.example.domain.model.isConfiguredForPayments
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
fun AdminPaymentsAndApprovalsTab(
    paymentConfigs: List<PaymentMethodConfigEntity>,
    requests: List<SubscriptionRequestEntity>,
    defaultDailyMins: Int,
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
    onApprove: (requestId: String, days: Int, dailyMinutes: Int) -> Unit,
    onReject: (requestId: String, reason: String) -> Unit
) {
    var selectedMethodCode by rememberSaveable { mutableStateOf(MobileMoneyMethod.YAS_MONEY.code) }
    val selectedConfig = paymentConfigs.find { it.methodCode == selectedMethodCode }
        ?: PaymentMethodConfigEntity(
            methodCode = selectedMethodCode,
            displayName = MobileMoneyMethod.displayLabel(selectedMethodCode)
        )

    var isEnabled by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.isEnabled)
    }
    var accountNumber by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.receivingAccountNumber)
    }
    var accountName by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.receivingAccountName)
    }
    var currency by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.currency.ifBlank { "MGA" })
    }
    var monthlyPrice by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.monthlyPrice)
    }
    var quarterlyPrice by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.quarterlyPrice)
    }
    var yearlyPrice by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.yearlyPrice)
    }
    var instructions by rememberSaveable(selectedConfig.methodCode, selectedConfig.updatedAt) {
        mutableStateOf(selectedConfig.transferInstructions)
    }

    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var dailyMinsText by rememberSaveable { mutableStateOf(defaultDailyMins.toString()) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    val filteredRequests = remember(requests, statusFilter) {
        if (statusFilter == "ALL") requests else requests.filter { it.status == statusFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FuturisticCard(accentBorder = true) {
            Text(
                text = "Mobile-Money Receiving Accounts & Plan Pricing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Configure verified receiving details for Yas Money, Airtel Money, and Orange Money. Unconfigured methods cannot accept learner submissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileMoneyMethod.entries.forEach { method ->
                    val cfg = paymentConfigs.find { it.methodCode == method.code }
                    val ready = cfg?.isConfiguredForPayments() == true
                    FilterChip(
                        selected = selectedMethodCode == method.code,
                        onClick = { selectedMethodCode = method.code },
                        label = { Text(if (ready) "${method.displayName} ✓" else method.displayName) },
                        modifier = Modifier.testTag("admin_method_chip_${method.code}")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPillBadge(
                    label = when {
                        !isEnabled -> "METHOD DISABLED"
                        selectedConfig.isConfiguredForPayments() -> "CONFIGURED & LIVE"
                        else -> "RECEIVING DETAILS REQUIRED"
                    },
                    color = when {
                        !isEnabled -> RoseError
                        selectedConfig.isConfiguredForPayments() -> EmeraldSuccess
                        else -> AmberWarning
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Enabled", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it },
                label = { Text("${selectedConfig.displayName} Receiving Account / Phone Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_pay_account_number_input")
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Account Holder Name") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("admin_pay_account_name_input")
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase() },
                    label = { Text("Currency") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("admin_pay_currency_input")
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = monthlyPrice,
                    onValueChange = { monthlyPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monthly (30d)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_pay_monthly_price_input")
                )
                OutlinedTextField(
                    value = quarterlyPrice,
                    onValueChange = { quarterlyPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quarterly (90d)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_pay_quarterly_price_input")
                )
                OutlinedTextField(
                    value = yearlyPrice,
                    onValueChange = { yearlyPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Yearly (365d)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_pay_yearly_price_input")
                )
            }

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Transfer Instructions Shown to Learners") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_pay_instructions_input")
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onSavePaymentConfig(
                        selectedMethodCode,
                        isEnabled,
                        accountNumber,
                        accountName,
                        currency,
                        monthlyPrice,
                        quarterlyPrice,
                        yearlyPrice,
                        instructions
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_save_payment_config_button")
            ) {
                Text("Save ${selectedConfig.displayName} Details")
            }
        }

        FuturisticCard {
            Text(
                text = "Payment Verification & Approval Queue (${requests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = dailyMinsText,
                onValueChange = { dailyMinsText = it.filter { c -> c.isDigit() } },
                label = { Text("Approved Daily Premium Allowance (Minutes)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "PENDING", "APPROVED", "REJECTED").forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text(st) }
                    )
                }
            }
        }

        if (filteredRequests.isEmpty()) {
            FuturisticCard {
                Text("No payment or subscription requests match filter '$statusFilter'.")
            }
        }

        filteredRequests.forEach { req ->
            var durationDaysText by rememberSaveable(req.id) {
                mutableStateOf(
                    PremiumPlanOption.defaultDurationDaysFor(req.requestedPlan, req.durationDays.coerceAtLeast(30)).toString()
                )
            }
            var rejectionNote by rememberSaveable(req.id) { mutableStateOf("") }

            FuturisticCard(accentBorder = req.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${req.userName} (${req.userEmail})", fontWeight = FontWeight.Bold)
                        Text(
                            text = "ID: ${req.id} • Submitted ${dateFormat.format(Date(req.createdAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPillBadge(
                        label = req.status,
                        color = when (req.status) {
                            "APPROVED" -> EmeraldSuccess
                            "PENDING" -> AmberWarning
                            else -> RoseError
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Selected Plan: ${PremiumPlanOption.displayTitleFor(req.requestedPlan)} (${req.durationDays} days)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (req.paymentMethod.isNotBlank()) {
                    Text(
                        text = "Method: ${MobileMoneyMethod.displayLabel(req.paymentMethod)} • Amount: ${req.amountPaid} ${req.currency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Transaction Ref: ${req.transactionReference} • Payment Date: ${req.paymentDate}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (req.proofOfPaymentUri.isNotBlank()) {
                    Text(
                        text = "Proof Attachment URI: ${req.proofOfPaymentUri} (Verify transfer receipt manually before approving)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }
                if (req.optionalMessage.isNotBlank()) {
                    Text("Learner note: \"${req.optionalMessage}\"", style = MaterialTheme.typography.bodySmall)
                }

                if (req.status == "PENDING") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationDaysText,
                        onValueChange = { durationDaysText = it.filter { c -> c.isDigit() } },
                        label = { Text("Approved Duration (Days)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = rejectionNote,
                        onValueChange = { rejectionNote = it },
                        label = { Text("Reason if rejecting (e.g., reference not found)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onApprove(
                                    req.id,
                                    durationDaysText.toIntOrNull() ?: req.durationDays.coerceAtLeast(30),
                                    dailyMinsText.toIntOrNull() ?: 120
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("approve_req_${req.id}")
                        ) {
                            Text("Verify & Approve")
                        }
                        Button(
                            onClick = { onReject(req.id, rejectionNote) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reject_req_${req.id}")
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }
}
