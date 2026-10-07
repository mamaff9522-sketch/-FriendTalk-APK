package com.example.companion.model

data class CompanionTag(
    val id: String,
    val name: String,
    val icon: String = "✨"
)

data class CompanionProfile(
    val id: String,
    val userId: String,
    val displayName: String,
    val avatar: String,
    val age: Int,
    val bio: String,
    val voiceIntroUrl: String? = null,
    val voiceDurationSec: Int = 0,
    val level: CompanionLevel = CompanionLevel.BRONZE,
    val status: CompanionStatus = CompanionStatus.PENDING_REVIEW,
    val isReadyNow: Boolean = false,
    val totalHoursTalked: Double = 0.0,
    val totalSessionsCount: Int = 0,
    val ratingScore: Double = 5.0,
    val ratingsCount: Int = 0,
    val tags: List<CompanionTag> = emptyList(),
    val serviceTypes: List<CompanionServiceType> = listOf(CompanionServiceType.TEXT_CHAT, CompanionServiceType.VOICE_CALL),
    val rejectionReason: String? = null,
    val suspensionReason: String? = null,
    val penaltyHistoryCount: Int = 0,
    val appliedAt: String = "วันนี้",
    val reviewedAt: String? = null,
    val reviewedByAdminId: String? = null
)
