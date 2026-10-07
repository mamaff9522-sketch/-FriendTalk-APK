package com.example.companion.service

import com.example.companion.model.*

class CompanionSessionManager {

    fun createSession(
        companion: CompanionProfile,
        clientId: String,
        clientName: String,
        serviceType: CompanionServiceType,
        rate: Int
    ): CompanionSession {
        return CompanionSession(
            sessionId = "sess_${System.currentTimeMillis()}",
            companionId = companion.id,
            companionName = companion.displayName,
            companionAvatar = companion.avatar,
            clientId = clientId,
            clientName = clientName,
            serviceType = serviceType,
            state = SessionState.ACTIVE,
            rateCoinsPerMin = rate,
            startTimeTimestamp = System.currentTimeMillis()
        )
    }

    fun endSession(session: CompanionSession, durationSeconds: Int): CompanionSession {
        val minutes = kotlin.math.max(1, (durationSeconds + 59) / 60)
        val totalCharged = minutes * session.rateCoinsPerMin
        val platformFee = (totalCharged * 0.2).toInt()
        val companionEarned = totalCharged - platformFee

        return session.copy(
            state = SessionState.ENDED,
            durationSeconds = durationSeconds,
            totalCoinsCharged = totalCharged,
            companionCoinsEarned = companionEarned,
            platformFeeCoins = platformFee
        )
    }
}
