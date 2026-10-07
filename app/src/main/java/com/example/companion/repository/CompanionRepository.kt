package com.example.companion.repository

import com.example.companion.data.CompanionMockData
import com.example.companion.model.*
import com.example.companion.service.CompanionLevelCalculator
import com.example.companion.service.CompanionModerationService
import com.example.companion.service.CompanionWalletService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CompanionRepository {
    val profiles: StateFlow<List<CompanionProfile>>
    val pricingConfig: StateFlow<CompanionPricingConfig>
    val wallets: StateFlow<Map<String, CompanionWallet>>
    val giftRequests: StateFlow<List<CompanionGiftRequest>>
    val auditLogs: StateFlow<List<CompanionAdminAuditLog>>
    val safetyReports: StateFlow<List<CompanionSafetyReport>>

    fun applyAsCompanion(profile: CompanionProfile)
    fun adminApproveCompanion(companionId: String, adminId: String, adminName: String, justification: String)
    fun adminRejectCompanion(companionId: String, adminId: String, adminName: String, reason: String)
    fun adminSuspendCompanion(companionId: String, adminId: String, adminName: String, reason: String)
    fun updatePricingRates(level: CompanionLevel, text: Int, voice: Int, video: Int, adminId: String, adminName: String, reason: String)
    fun recalculateLevel(companionId: String)
    fun sendGiftRequest(request: CompanionGiftRequest)
    fun respondGiftRequest(requestId: String, accept: Boolean): Pair<Boolean, CompanionGiftRequest?>
    fun creditSessionEarning(companionId: String, session: CompanionSession, coinsEarned: Int)
    fun requestWithdrawal(companionId: String, companionName: String, amountCoins: Int, bankAccount: CompanionBankAccount): Boolean
    fun logAdminAudit(adminId: String, adminName: String, action: String, targetType: String, targetId: String, reason: String)
    fun submitSafetyReport(report: CompanionSafetyReport)
}

class InMemoryCompanionRepository(
    private val walletService: CompanionWalletService = CompanionWalletService(),
    private val moderationService: CompanionModerationService = CompanionModerationService()
) : CompanionRepository {

    private val _profiles = MutableStateFlow(CompanionMockData.initialProfiles)
    override val profiles: StateFlow<List<CompanionProfile>> = _profiles.asStateFlow()

    private val _pricingConfig = MutableStateFlow(CompanionPricingConfig())
    override val pricingConfig: StateFlow<CompanionPricingConfig> = _pricingConfig.asStateFlow()

    private val _wallets = MutableStateFlow(CompanionMockData.initialWallets)
    override val wallets: StateFlow<Map<String, CompanionWallet>> = _wallets.asStateFlow()

    private val _giftRequests = MutableStateFlow(CompanionMockData.initialGiftRequests)
    override val giftRequests: StateFlow<List<CompanionGiftRequest>> = _giftRequests.asStateFlow()

    private val _auditLogs = MutableStateFlow(CompanionMockData.initialAuditLogs)
    override val auditLogs: StateFlow<List<CompanionAdminAuditLog>> = _auditLogs.asStateFlow()

    private val _safetyReports = MutableStateFlow(CompanionMockData.initialSafetyReports)
    override val safetyReports: StateFlow<List<CompanionSafetyReport>> = _safetyReports.asStateFlow()

    override fun applyAsCompanion(profile: CompanionProfile) {
        val newProfile = profile.copy(
            status = CompanionStatus.PENDING_REVIEW,
            isReadyNow = false
        )
        _profiles.value = listOf(newProfile) + _profiles.value.filter { it.userId != profile.userId }
    }

    override fun adminApproveCompanion(companionId: String, adminId: String, adminName: String, justification: String) {
        _profiles.value = _profiles.value.map {
            if (it.id == companionId) {
                it.copy(
                    status = CompanionStatus.APPROVED,
                    reviewedAt = "วันนี้",
                    reviewedByAdminId = adminId,
                    rejectionReason = null,
                    suspensionReason = null,
                    isReadyNow = true
                )
            } else it
        }
        logAdminAudit(
            adminId = adminId,
            adminName = adminName,
            action = "อนุมัติคำขอเป็นเพื่อนคุย",
            targetType = "companion_application",
            targetId = companionId,
            reason = justification
        )
    }

    override fun adminRejectCompanion(companionId: String, adminId: String, adminName: String, reason: String) {
        _profiles.value = _profiles.value.map {
            if (it.id == companionId) {
                it.copy(
                    status = CompanionStatus.REJECTED,
                    reviewedAt = "วันนี้",
                    reviewedByAdminId = adminId,
                    rejectionReason = reason,
                    isReadyNow = false
                )
            } else it
        }
        logAdminAudit(
            adminId = adminId,
            adminName = adminName,
            action = "ปฏิเสธคำขอเป็นเพื่อนคุย",
            targetType = "companion_application",
            targetId = companionId,
            reason = reason
        )
    }

    override fun adminSuspendCompanion(companionId: String, adminId: String, adminName: String, reason: String) {
        _profiles.value = _profiles.value.map {
            if (it.id == companionId) {
                it.copy(
                    status = CompanionStatus.SUSPENDED,
                    suspensionReason = reason,
                    isReadyNow = false,
                    penaltyHistoryCount = it.penaltyHistoryCount + 1
                )
            } else it
        }
        logAdminAudit(
            adminId = adminId,
            adminName = adminName,
            action = "ระงับการให้บริการเพื่อนคุย (Suspend)",
            targetType = "companion_status",
            targetId = companionId,
            reason = reason
        )
    }

    override fun updatePricingRates(
        level: CompanionLevel,
        text: Int,
        voice: Int,
        video: Int,
        adminId: String,
        adminName: String,
        reason: String
    ) {
        val currentRates = _pricingConfig.value.rates.toMutableMap()
        currentRates[level] = CompanionLevelRate(
            level = level,
            textChatCoinsPerMin = text,
            voiceCallCoinsPerMin = voice,
            videoCallCoinsPerMin = video
        )
        _pricingConfig.value = CompanionPricingConfig(rates = currentRates)
        logAdminAudit(
            adminId = adminId,
            adminName = adminName,
            action = "แก้ไขเรตราคา Level ${level.name}",
            targetType = "companion_pricing",
            targetId = level.name,
            reason = "Text: $text, Voice: $voice, Video: $video coins/min ($reason)"
        )
    }

    override fun recalculateLevel(companionId: String) {
        _profiles.value = _profiles.value.map {
            if (it.id == companionId) {
                CompanionLevelCalculator.recalculateProfileLevel(it)
            } else it
        }
    }

    override fun sendGiftRequest(request: CompanionGiftRequest) {
        _giftRequests.value = listOf(request) + _giftRequests.value
    }

    override fun respondGiftRequest(requestId: String, accept: Boolean): Pair<Boolean, CompanionGiftRequest?> {
        val target = _giftRequests.value.find { it.id == requestId } ?: return Pair(false, null)
        val newStatus = if (accept) GiftRequestStatus.ACCEPTED else GiftRequestStatus.REJECTED
        val updated = target.copy(status = newStatus, respondedAtTimestamp = System.currentTimeMillis())

        _giftRequests.value = _giftRequests.value.map { if (it.id == requestId) updated else it }

        if (accept) {
            val receiverCompanion = _profiles.value.find { it.userId == target.receiverId }
            val companionId = receiverCompanion?.id ?: "comp_${target.receiverId}"
            val currentWallet = _wallets.value[companionId] ?: CompanionWallet(companionId = companionId)
            val earnedCoins = (target.gift.coins * 0.8).toInt()
            val updatedWallet = walletService.creditGiftReward(currentWallet, updated, earnedCoins)
            _wallets.value = _wallets.value + (companionId to updatedWallet)
        }

        return Pair(true, updated)
    }

    override fun creditSessionEarning(companionId: String, session: CompanionSession, coinsEarned: Int) {
        val currentWallet = _wallets.value[companionId] ?: CompanionWallet(companionId = companionId)
        val updatedWallet = walletService.creditSessionEarning(currentWallet, session, coinsEarned)
        _wallets.value = _wallets.value + (companionId to updatedWallet)

        _profiles.value = _profiles.value.map {
            if (it.id == companionId) {
                val updatedProf = it.copy(
                    totalSessionsCount = it.totalSessionsCount + 1,
                    totalHoursTalked = it.totalHoursTalked + (session.durationSeconds / 3600.0)
                )
                CompanionLevelCalculator.recalculateProfileLevel(updatedProf)
            } else it
        }
    }

    override fun requestWithdrawal(
        companionId: String,
        companionName: String,
        amountCoins: Int,
        bankAccount: CompanionBankAccount
    ): Boolean {
        val currentWallet = _wallets.value[companionId] ?: return false
        val result = walletService.requestWithdrawal(currentWallet, amountCoins, bankAccount) ?: return false
        val (updatedWallet, _) = result
        _wallets.value = _wallets.value + (companionId to updatedWallet)
        return true
    }

    override fun logAdminAudit(
        adminId: String,
        adminName: String,
        action: String,
        targetType: String,
        targetId: String,
        reason: String
    ) {
        val log = moderationService.createAuditLog(
            adminId = adminId,
            adminName = adminName,
            action = action,
            targetType = targetType,
            targetId = targetId,
            reason = reason
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
    }

    override fun submitSafetyReport(report: CompanionSafetyReport) {
        _safetyReports.value = listOf(report) + _safetyReports.value
    }
}
