package com.example.companion.service

import com.example.companion.model.CompanionAdminAuditLog
import com.example.companion.model.CompanionSafetyReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CompanionModerationService {

    fun createAuditLog(
        adminId: String,
        adminName: String,
        action: String,
        targetType: String,
        targetId: String,
        reason: String
    ): CompanionAdminAuditLog {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        return CompanionAdminAuditLog(
            id = "audit_${System.currentTimeMillis()}",
            adminId = adminId,
            adminName = adminName,
            action = action,
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            timestamp = dateStr
        )
    }

    fun createSafetyReport(
        reporterId: String,
        reporterName: String,
        companionId: String,
        companionName: String,
        category: String,
        evidence: String
    ): CompanionSafetyReport {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        return CompanionSafetyReport(
            id = "rep_${System.currentTimeMillis()}",
            reporterId = reporterId,
            reporterName = reporterName,
            companionId = companionId,
            companionName = companionName,
            category = category,
            evidenceText = evidence,
            timestamp = dateStr,
            isResolved = false
        )
    }
}
