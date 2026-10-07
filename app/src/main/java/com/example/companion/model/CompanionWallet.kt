package com.example.companion.model

data class CompanionBankAccount(
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String,
    val promptPay: String? = null
)

enum class CompanionTransactionType {
    SESSION_INCOME, GIFT_REWARD, WITHDRAWAL, PENALTY_DEDUCTION
}

data class CompanionWalletTransaction(
    val id: String,
    val companionId: String,
    val type: CompanionTransactionType,
    val amountCoins: Int,
    val amountBahtEquivalent: Double,
    val description: String,
    val timestamp: String,
    val referenceId: String? = null
)

data class CompanionWallet(
    val companionId: String,
    val availableCoins: Int = 0,
    val pendingCoins: Int = 0,
    val totalCoinsEarned: Int = 0,
    val totalWithdrawnBaht: Double = 0.0,
    val bankAccount: CompanionBankAccount? = null,
    val transactions: List<CompanionWalletTransaction> = emptyList()
)
