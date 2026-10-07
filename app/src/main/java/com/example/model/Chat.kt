package com.example.model

enum class MessageType {
    TEXT, IMAGE, AUDIO, GIFT, SYSTEM, CALL_LOG
}

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val conversationId: String,
    val type: MessageType = MessageType.TEXT,
    val text: String = "",
    val mediaUrl: String? = null,
    val giftId: String? = null,
    val giftCoins: Int = 0,
    val timestamp: String,
    val isRead: Boolean = true
)

data class Conversation(
    val id: String,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupAvatar: String? = null,
    val participantIds: List<String>,
    val participants: List<User> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: String = "",
    val unreadCounts: Map<String, Int> = emptyMap(),
    val isPinned: Boolean = false
)

enum class CallType {
    VOICE, VIDEO
}

enum class CallStatus {
    RINGING, CONNECTED, ENDED, MISSED
}

data class CallSession(
    val id: String,
    val callerId: String,
    val receiverId: String,
    val partnerName: String,
    val partnerAvatar: String,
    val type: CallType,
    val status: CallStatus,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isCameraOff: Boolean = false,
    val isSpeakerOn: Boolean = false
)
