package com.example.domain.model

import com.example.data.local.PaymentMethodConfigEntity

/**
 * Supported mobile-money providers for Interna Premium subscriptions.
 * Receiving account numbers, account names, prices, currencies, and transfer instructions
 * are never hardcoded or invented; they must be configured by the administrator in Developer Console.
 */
enum class MobileMoneyMethod(
    val code: String,
    val displayName: String
) {
    YAS_MONEY("YAS_MONEY", "Yas Money"),
    AIRTEL_MONEY("AIRTEL_MONEY", "Airtel Money"),
    ORANGE_MONEY("ORANGE_MONEY", "Orange Money");

    companion object {
        fun fromCode(code: String): MobileMoneyMethod? =
            entries.find { it.code.equals(code.trim(), ignoreCase = true) }

        fun displayLabel(code: String): String =
            fromCode(code)?.displayName ?: code.ifBlank { "Manual Request" }
    }
}

enum class PremiumPlanOption(
    val planCode: String,
    val title: String,
    val durationDays: Int,
    val dailyMinutesAllowance: Int
) {
    MONTHLY("PREMIUM_MONTHLY", "Premium Monthly (30 Days)", 30, 120),
    QUARTERLY("PREMIUM_QUARTERLY", "Premium Quarterly (90 Days)", 90, 120),
    YEARLY("PREMIUM_YEARLY", "Premium Yearly (365 Days)", 365, 120);

    fun configuredPrice(config: PaymentMethodConfigEntity?): String {
        if (config == null) return ""
        return when (this) {
            MONTHLY -> config.monthlyPrice
            QUARTERLY -> config.quarterlyPrice
            YEARLY -> config.yearlyPrice
        }.trim()
    }

    companion object {
        fun fromPlanCode(code: String): PremiumPlanOption? =
            entries.find { it.planCode.equals(code.trim(), ignoreCase = true) }

        fun defaultDurationDaysFor(code: String, fallbackDays: Int = 30): Int =
            fromPlanCode(code)?.durationDays ?: fallbackDays

        fun displayTitleFor(code: String): String =
            fromPlanCode(code)?.title ?: when (code.uppercase()) {
                "PREMIUM_ONLINE" -> "Premium Online"
                "FREE_OFFLINE" -> "Free Plan"
                else -> code
            }

        fun isPremiumPlanCode(code: String): Boolean =
            code.trim().uppercase().startsWith("PREMIUM")
    }
}

fun PaymentMethodConfigEntity.isConfiguredForPayments(): Boolean {
    return isEnabled &&
        receivingAccountNumber.isNotBlank() &&
        receivingAccountName.isNotBlank() &&
        currency.isNotBlank()
}
