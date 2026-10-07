package com.example.model

enum class ReportStatus {
    PENDING, REVIEWED, RESOLVED, DISMISSED
}

data class Report(
    val id: String,
    val reporterId: String,
    val reporterName: String,
    val targetType: String, // USER, POST, LIVE, CHAT
    val targetId: String,
    val targetName: String,
    val reason: String,
    val details: String = "",
    val timestamp: String,
    val status: ReportStatus = ReportStatus.PENDING
)

data class WithdrawalRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val amountDiamonds: Int,
    val amountBaht: Double,
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val requestedAt: String = "วันนี้ 10:30"
)

data class AdminLog(
    val id: String,
    val adminName: String,
    val action: String,
    val details: String,
    val timestamp: String
)

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String, // LIKE, COMMENT, GIFT, SYSTEM, MATCH
    val timestamp: String,
    val isRead: Boolean = false,
    val avatarUrl: String? = null
)

data class AppPermissions(
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
    val notifications: Boolean = false
)
