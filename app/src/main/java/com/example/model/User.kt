package com.example.model

enum class Gender {
    MALE, FEMALE, OTHER
}

enum class UserRole {
    USER, CREATOR, MODERATOR, SUPERADMIN
}

data class UserLocation(
    val province: String = "กรุงเทพมหานคร",
    val distanceKm: Double = 0.0,
    val isSharingLocation: Boolean = true
)

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val coverPhoto: String = "",
    val age: Int = 20,
    val gender: Gender = Gender.OTHER,
    val bio: String = "",
    val role: UserRole = UserRole.USER,
    val isCreator: Boolean = false,
    val isVerified: Boolean = false,
    val isOnline: Boolean = true,
    val lastActive: String = "ออนไลน์ขณะนี้",
    val coins: Int = 1000,
    val diamonds: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val likesCount: Int = 0,
    val location: UserLocation = UserLocation(),
    val photos: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val giftsReceivedTotal: Int = 0
)
