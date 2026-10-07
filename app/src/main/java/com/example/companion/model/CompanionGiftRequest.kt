package com.example.companion.model

import com.example.model.GiftItem

data class CompanionGiftRequest(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val receiverId: String,
    val receiverName: String,
    val gift: GiftItem,
    val note: String = "",
    val requestedAtTimestamp: Long = System.currentTimeMillis(),
    val status: GiftRequestStatus = GiftRequestStatus.PENDING,
    val respondedAtTimestamp: Long? = null
)
