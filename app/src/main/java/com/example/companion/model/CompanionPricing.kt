package com.example.companion.model

data class CompanionLevelRate(
    val level: CompanionLevel,
    val textChatCoinsPerMin: Int,
    val voiceCallCoinsPerMin: Int,
    val videoCallCoinsPerMin: Int
)

data class CompanionPricingConfig(
    val platformFeePercent: Double = 20.0,
    val rates: Map<CompanionLevel, CompanionLevelRate> = mapOf(
        CompanionLevel.BRONZE to CompanionLevelRate(CompanionLevel.BRONZE, textChatCoinsPerMin = 10, voiceCallCoinsPerMin = 30, videoCallCoinsPerMin = 60),
        CompanionLevel.SILVER to CompanionLevelRate(CompanionLevel.SILVER, textChatCoinsPerMin = 15, voiceCallCoinsPerMin = 45, videoCallCoinsPerMin = 90),
        CompanionLevel.GOLD to CompanionLevelRate(CompanionLevel.GOLD, textChatCoinsPerMin = 25, voiceCallCoinsPerMin = 60, videoCallCoinsPerMin = 120),
        CompanionLevel.PLATINUM to CompanionLevelRate(CompanionLevel.PLATINUM, textChatCoinsPerMin = 35, voiceCallCoinsPerMin = 80, videoCallCoinsPerMin = 160),
        CompanionLevel.DIAMOND to CompanionLevelRate(CompanionLevel.DIAMOND, textChatCoinsPerMin = 50, voiceCallCoinsPerMin = 120, videoCallCoinsPerMin = 240)
    )
)
