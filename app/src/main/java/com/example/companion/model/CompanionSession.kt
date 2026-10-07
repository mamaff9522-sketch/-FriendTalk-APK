package com.example.companion.model

enum class SessionState {
    IDLE, CONNECTING, ACTIVE, PAUSED_INSUFFICIENT_FUNDS, ENDED
}

data class CompanionSession(
    val sessionId: String,
    val companionId: String,
    val companionName: String,
    val companionAvatar: String,
    val clientId: String,
    val clientName: String,
    val serviceType: CompanionServiceType,
    val state: SessionState = SessionState.IDLE,
    val rateCoinsPerMin: Int,
    val startTimeTimestamp: Long = 0L,
    val durationSeconds: Int = 0,
    val totalCoinsCharged: Int = 0,
    val companionCoinsEarned: Int = 0,
    val platformFeeCoins: Int = 0
)
