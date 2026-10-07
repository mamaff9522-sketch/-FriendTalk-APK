package com.example.companion.service

import com.example.companion.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CompanionWalletService {

    fun creditGiftReward(wallet: CompanionWallet, giftRequest: CompanionGiftRequest, earnedCoins: Int): CompanionWallet {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val tx = CompanionWalletTransaction(
            id = "tx_${System.currentTimeMillis()}",
            companionId = wallet.companionId,
            type = CompanionTransactionType.GIFT_REWARD,
            amountCoins = earnedCoins,
            amountBahtEquivalent = earnedCoins * 0.2, // 5 coins = 1 Baht
            description = "ได้รับของขวัญ ${giftRequest.gift.name} จาก ${giftRequest.senderName}",
            timestamp = dateStr,
            referenceId = giftRequest.id
        )

        return wallet.copy(
            availableCoins = wallet.availableCoins + earnedCoins,
            totalCoinsEarned = wallet.totalCoinsEarned + earnedCoins,
            transactions = listOf(tx) + wallet.transactions
        )
    }

    fun creditSessionEarning(wallet: CompanionWallet, session: CompanionSession, coinsEarned: Int): CompanionWallet {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val tx = CompanionWalletTransaction(
            id = "tx_${System.currentTimeMillis()}",
            companionId = wallet.companionId,
            type = CompanionTransactionType.SESSION_INCOME,
            amountCoins = coinsEarned,
            amountBahtEquivalent = coinsEarned * 0.2,
            description = "รายได้จากเซสชันคุยกับ ${session.clientName} (${session.durationSeconds / 60} นาที)",
            timestamp = dateStr,
            referenceId = session.sessionId
        )

        return wallet.copy(
            availableCoins = wallet.availableCoins + coinsEarned,
            totalCoinsEarned = wallet.totalCoinsEarned + coinsEarned,
            transactions = listOf(tx) + wallet.transactions
        )
    }

    fun requestWithdrawal(
        wallet: CompanionWallet,
        amountCoins: Int,
        bankAccount: CompanionBankAccount
    ): Pair<CompanionWallet, CompanionWalletTransaction>? {
        if (wallet.availableCoins < amountCoins || amountCoins <= 0) return null

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        val baht = amountCoins * 0.2
        val tx = CompanionWalletTransaction(
            id = "tx_wd_${System.currentTimeMillis()}",
            companionId = wallet.companionId,
            type = CompanionTransactionType.WITHDRAWAL,
            amountCoins = -amountCoins,
            amountBahtEquivalent = baht,
            description = "คำขอถอนเงินเข้าบัญชี ${bankAccount.bankName} (${bankAccount.accountNumber})",
            timestamp = dateStr
        )

        val updated = wallet.copy(
            availableCoins = wallet.availableCoins - amountCoins,
            totalWithdrawnBaht = wallet.totalWithdrawnBaht + baht,
            bankAccount = bankAccount,
            transactions = listOf(tx) + wallet.transactions
        )

        return Pair(updated, tx)
    }
}
