package com.example.companion.model

enum class CompanionLevel {
    BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
}

enum class CompanionStatus {
    DRAFT, PENDING_REVIEW, APPROVED, REJECTED, SUSPENDED
}

enum class CompanionServiceType {
    TEXT_CHAT, VOICE_CALL, VIDEO_CALL
}

enum class GiftRequestStatus {
    PENDING, ACCEPTED, REJECTED, EXPIRED
}

enum class KycStatus {
    NOT_SUBMITTED, PENDING, VERIFIED, REJECTED
}
