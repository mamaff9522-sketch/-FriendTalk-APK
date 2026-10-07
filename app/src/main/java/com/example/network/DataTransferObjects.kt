package com.example.network

import com.example.model.*
import com.example.companion.model.*

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

data class CreatePostRequest(
    val authorId: String,
    val content: String,
    val type: String,
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    val tags: List<String> = emptyList()
)

data class SendMessageRequest(
    val conversationId: String,
    val senderId: String,
    val text: String,
    val type: String = "TEXT"
)

data class CompanionActionRequest(
    val companionId: String,
    val adminId: String,
    val reason: String
)
