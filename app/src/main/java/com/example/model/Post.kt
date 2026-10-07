package com.example.model

enum class PostType {
    TEXT,
    IMAGE,
    MULTI_IMAGE,
    VIDEO,
    SHORT_VIDEO,
    LIVE,
    SHARED_POST
}

data class PostComment(
    val id: String,
    val postId: String,
    val userId: String,
    val userDisplayName: String,
    val userAvatar: String,
    val content: String,
    val timestamp: String,
    val likesCount: Int = 0,
    val isLiked: Boolean = false
)

data class SharedPostContent(
    val originalPostId: String,
    val originalAuthorName: String,
    val originalAuthorAvatar: String,
    val originalContent: String,
    val originalImageUrl: String? = null,
    val originalTimestamp: String = ""
)

data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val authorUsername: String,
    val authorIsVerified: Boolean = false,
    val authorBadges: List<String> = emptyList(),
    val location: String? = null,
    val timestamp: String,
    val type: PostType = PostType.TEXT,
    val content: String,
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoDuration: String? = null,
    val liveRoomId: String? = null,
    val liveViewerCount: Int = 0,
    val sharedPost: SharedPostContent? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val comments: List<PostComment> = emptyList(),
    val tags: List<String> = emptyList()
)

data class Story(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String,
    val imageUrl: String,
    val timestamp: String,
    val isViewed: Boolean = false,
    val isLiveNow: Boolean = false,
    val liveViewerCount: Int = 0
)
