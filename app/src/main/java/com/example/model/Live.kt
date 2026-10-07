package com.example.model

data class LiveComment(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String,
    val text: String,
    val isGift: Boolean = false,
    val giftIcon: String? = null
)

data class LiveViewer(
    val id: String,
    val name: String,
    val avatar: String
)

data class TopGifterRecord(
    val rank: Int,
    val userId: String,
    val userName: String,
    val userAvatar: String,
    val coinsContributed: Int
)

data class GiftItem(
    val id: String,
    val name: String,
    val icon: String,
    val coins: Int,
    val animationEffect: String = "POP"
)

data class ActiveGiftAnimation(
    val id: String,
    val gift: GiftItem,
    val senderName: String,
    val senderAvatar: String
)

data class PkState(
    val isPkActive: Boolean = false,
    val opponentId: String? = null,
    val opponentName: String? = null,
    val opponentAvatar: String? = null,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val timeLeftSeconds: Int = 180
)

data class LiveRoom(
    val id: String,
    val hostId: String,
    val hostName: String,
    val hostAvatar: String,
    val hostUsername: String,
    val title: String,
    val coverUrl: String,
    val tags: List<String> = emptyList(),
    val viewerCount: Int = 0,
    val likesCount: Int = 0,
    val diamondsEarned: Int = 0,
    val isLive: Boolean = true,
    val isPkMode: Boolean = false,
    val pkState: PkState = PkState(),
    val recentComments: List<LiveComment> = emptyList(),
    val topGifters: List<TopGifterRecord> = emptyList()
)
