package com.example.companion.data

import com.example.companion.model.*
import com.example.data.MockData

object CompanionMockData {
    val initialProfiles = listOf(
        CompanionProfile(
            id = "comp_linlin",
            userId = "user_linlin",
            displayName = "หลินหลิน 🌸",
            avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            age = 22,
            bio = "พร้อมรับฟังและให้กำลังใจทุกเรื่อง คุยสนุก อารมณ์ดี รับฟังปัญหาไม่ตัดสินใครค่ะ 💬✨",
            level = CompanionLevel.GOLD,
            status = CompanionStatus.APPROVED,
            isReadyNow = true,
            totalHoursTalked = 45.5,
            totalSessionsCount = 120,
            ratingScore = 4.95,
            ratingsCount = 98,
            tags = listOf(CompanionTag("t1", "ผู้ฟังที่ดี", "👂"), CompanionTag("t2", "ให้กำลังใจ", "💪"), CompanionTag("t3", "เสียงใส", "🎙️")),
            serviceTypes = listOf(CompanionServiceType.TEXT_CHAT, CompanionServiceType.VOICE_CALL, CompanionServiceType.VIDEO_CALL),
            appliedAt = "01/10/2026",
            reviewedAt = "01/10/2026",
            reviewedByAdminId = "user_admin"
        ),
        CompanionProfile(
            id = "comp_fah",
            userId = "user_fah",
            displayName = "ฟ้าใส (Fahsai)",
            avatar = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80",
            age = 23,
            bio = "เพื่อนคุยสายศิลปะ ปรึกษาเรื่องงาน ดีไซน์ และชีวิตประจำวัน สบายๆ เป็นกันเอง ☕🎨",
            level = CompanionLevel.SILVER,
            status = CompanionStatus.APPROVED,
            isReadyNow = true,
            totalHoursTalked = 22.0,
            totalSessionsCount = 58,
            ratingScore = 4.88,
            ratingsCount = 45,
            tags = listOf(CompanionTag("t4", "สายชิลล์", "☕"), CompanionTag("t5", "ที่ปรึกษา", "💡")),
            serviceTypes = listOf(CompanionServiceType.TEXT_CHAT, CompanionServiceType.VOICE_CALL),
            appliedAt = "02/10/2026",
            reviewedAt = "02/10/2026",
            reviewedByAdminId = "user_admin"
        )
    )

    val initialWallets = mapOf(
        "comp_linlin" to CompanionWallet(
            companionId = "comp_linlin",
            availableCoins = 4200,
            pendingCoins = 500,
            totalCoinsEarned = 12500,
            totalWithdrawnBaht = 1600.0,
            bankAccount = CompanionBankAccount("ธนาคารกสิกรไทย", "123-4-56789-0", "น.ส. หลินหลิน สดใส", "081-234-5678"),
            transactions = listOf(
                CompanionWalletTransaction("tx_1", "comp_linlin", CompanionTransactionType.SESSION_INCOME, 300, 60.0, "ค่าบริการโทรคุย 10 นาที", "วันนี้ 11:30"),
                CompanionWalletTransaction("tx_2", "comp_linlin", CompanionTransactionType.GIFT_REWARD, 400, 80.0, "ได้รับของขวัญ มงกุฎทอง (80%)", "เมื่อวาน 20:15")
            )
        )
    )

    val initialGiftRequests = listOf(
        CompanionGiftRequest(
            id = "greq_1",
            senderId = "user_me",
            senderName = "ต้นกล้า",
            senderAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80",
            receiverId = "user_linlin",
            receiverName = "หลินหลิน 🌸",
            gift = MockData.giftsList[3], // มงกุฎทอง
            note = "ขอบคุณสำหรับคำแนะนำดีๆ เมื่อคืนนี้นะครับ!",
            status = GiftRequestStatus.PENDING
        )
    )

    val initialAuditLogs = listOf(
        CompanionAdminAuditLog(
            id = "audit_1",
            adminId = "user_admin",
            adminName = "ณัฐพงษ์ (Super Admin)",
            action = "อนุมัติเพื่อนคุย",
            targetType = "companion_application",
            targetId = "comp_linlin",
            reason = "ผ่านการตรวจสอบ KYC อายุ 22 ปี และประวัติเรียบร้อย",
            timestamp = "01/10/2026 10:00"
        )
    )

    val initialSafetyReports = listOf(
        CompanionSafetyReport(
            id = "srep_1",
            reporterId = "user_fah",
            reporterName = "ฟ้าใส",
            companionId = "comp_unknown",
            companionName = "test_companion",
            category = "INAPPROPRIATE",
            evidenceText = "ใช้คำพูดไม่เหมาะสมระหว่างเซสชัน",
            timestamp = "03/10/2026 18:00",
            isResolved = true
        )
    )
}
