package com.example.companion.service

import com.example.companion.model.CompanionLevel
import com.example.companion.model.CompanionProfile

object CompanionLevelCalculator {
    fun recalculateProfileLevel(profile: CompanionProfile): CompanionProfile {
        val hours = profile.totalHoursTalked
        val rating = profile.ratingScore
        val sessions = profile.totalSessionsCount

        val calculatedLevel = when {
            hours >= 100 && rating >= 4.9 && sessions >= 200 -> CompanionLevel.DIAMOND
            hours >= 50 && rating >= 4.8 && sessions >= 100 -> CompanionLevel.PLATINUM
            hours >= 20 && rating >= 4.7 && sessions >= 40 -> CompanionLevel.GOLD
            hours >= 5 && rating >= 4.5 && sessions >= 10 -> CompanionLevel.SILVER
            else -> CompanionLevel.BRONZE
        }

        return profile.copy(level = calculatedLevel)
    }
}
