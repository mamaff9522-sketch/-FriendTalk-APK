package com.example.companion.model

data class CompanionAdminAuditLog(
    val id: String,
    val adminId: String,
    val adminName: String,
    val action: String, // APPROVE, REJECT, SUSPEND, UPDATE_PRICING, DEDUCT
    val targetType: String, // PROFILE, PRICING, WALLET
    val targetId: String,
    val reason: String,
    val timestamp: String
)

data class CompanionSafetyReport(
    val id: String,
    val reporterId: String,
    val reporterName: String,
    val companionId: String,
    val companionName: String,
    val category: String, // INAPPROPRIATE, SCAM, HARASSMENT, UNDERAGE
    val evidenceText: String,
    val timestamp: String,
    val isResolved: Boolean = false
)
