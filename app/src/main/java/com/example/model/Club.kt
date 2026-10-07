package com.example.model

data class ForumTopic(
    val id: String,
    val clubId: String,
    val authorName: String,
    val authorAvatar: String,
    val title: String,
    val preview: String,
    val repliesCount: Int = 0,
    val timestamp: String = "1 ชม. ที่แล้ว"
)

data class Club(
    val id: String,
    val name: String,
    val category: String,
    val icon: String,
    val coverUrl: String,
    val description: String,
    val membersCount: Int = 0,
    val isJoined: Boolean = false,
    val topics: List<ForumTopic> = emptyList()
)

data class ShortVideoItem(
    val id: String,
    val creatorId: String,
    val creatorName: String,
    val creatorAvatar: String,
    val caption: String,
    val videoUrl: String,
    val musicTitle: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false
)
