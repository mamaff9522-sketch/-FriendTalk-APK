package com.example.network

import com.example.companion.model.*
import com.example.model.*
import retrofit2.http.*

interface FriendTalkApiService {

    // --- Users & Profile ---
    @GET("api/v1/users/{userId}")
    suspend fun getUserProfile(@Path("userId") userId: String): ApiResponse<User>

    @PUT("api/v1/users/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: String,
        @Body user: User
    ): ApiResponse<User>

    // --- Feed & Posts ---
    @GET("api/v1/feed")
    suspend fun getFeed(
        @Query("category") category: String = "ALL",
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<Post>>

    @POST("api/v1/posts")
    suspend fun createPost(@Body request: CreatePostRequest): ApiResponse<Post>

    @POST("api/v1/posts/{postId}/like")
    suspend fun toggleLike(@Path("postId") postId: String): ApiResponse<Boolean>

    @POST("api/v1/posts/{postId}/comments")
    suspend fun addComment(
        @Path("postId") postId: String,
        @Body comment: PostComment
    ): ApiResponse<PostComment>

    // --- Live & Rooms ---
    @GET("api/v1/live/rooms")
    suspend fun getLiveRooms(): ApiResponse<List<LiveRoom>>

    @POST("api/v1/live/rooms/{roomId}/comments")
    suspend fun sendLiveComment(
        @Path("roomId") roomId: String,
        @Body comment: LiveComment
    ): ApiResponse<LiveComment>

    // --- Chat & Messages ---
    @GET("api/v1/conversations")
    suspend fun getConversations(): ApiResponse<List<Conversation>>

    @POST("api/v1/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): ApiResponse<ChatMessage>

    // --- Companion 18+ ---
    @GET("api/v1/companion/profiles")
    suspend fun getCompanionProfiles(): ApiResponse<List<CompanionProfile>>

    @POST("api/v1/companion/apply")
    suspend fun applyAsCompanion(@Body profile: CompanionProfile): ApiResponse<CompanionProfile>

    @POST("api/v1/companion/admin/approve")
    suspend fun adminApproveCompanion(@Body request: CompanionActionRequest): ApiResponse<Boolean>

    // --- Remote UI Config ---
    @GET("api/v1/ui/config")
    suspend fun getRemoteUiConfig(): ApiResponse<AppUiConfig>

    @POST("api/v1/ui/config/publish")
    suspend fun publishUiConfig(@Body config: AppUiConfig): ApiResponse<String>
}
