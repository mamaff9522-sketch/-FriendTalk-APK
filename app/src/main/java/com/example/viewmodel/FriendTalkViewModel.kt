package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.companion.data.CompanionMockData
import com.example.companion.model.*
import com.example.companion.repository.CompanionRepository
import com.example.companion.repository.InMemoryCompanionRepository
import com.example.companion.service.CompanionSessionManager
import com.example.data.MockData
import com.example.model.*
import com.example.service.RemoteUiConfigManager
import com.example.service.SoundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendTalkUiState(
    val currentUser: User = MockData.initialUsers[0],
    val users: List<User> = MockData.initialUsers,
    val posts: List<Post> = MockData.initialPosts,
    val stories: List<Story> = MockData.initialStories,
    val liveRooms: List<LiveRoom> = MockData.initialLiveRooms,
    val conversations: List<Conversation> = MockData.initialConversations,
    val messages: List<ChatMessage> = MockData.initialMessages,
    val clubs: List<Club> = MockData.initialClubs,
    val forumTopics: List<ForumTopic> = MockData.initialForumTopics,
    val reports: List<Report> = MockData.initialReports,
    val withdrawals: List<WithdrawalRequest> = MockData.initialWithdrawals,
    val adminLogs: List<AdminLog> = MockData.initialAdminLogs,
    val notifications: List<AppNotification> = MockData.initialNotifications,
    val permissions: AppPermissions = AppPermissions(),
    val followingIds: Set<String> = setOf("user_linlin", "user_arty", "user_fah"),
    val friendIds: Set<String> = setOf("user_linlin", "user_fah"),
    val blockedUserIds: Set<String> = setOf("user_spammer"),
    val activeTab: String = "home", // home, discover, live, chat, movie, profile, companion
    val selectedFeedCategory: String = "ทั้งหมด",
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val currentLiveRoomId: String? = null,
    val activeGiftAnimation: ActiveGiftAnimation? = null,
    val activeCall: CallSession? = null,
    val activeChatRoomId: String? = null,
    // Companion System States
    val companionProfiles: List<CompanionProfile> = CompanionMockData.initialProfiles,
    val companionPricingConfig: CompanionPricingConfig = CompanionPricingConfig(),
    val companionWallets: Map<String, CompanionWallet> = CompanionMockData.initialWallets,
    val companionGiftRequests: List<CompanionGiftRequest> = CompanionMockData.initialGiftRequests,
    val companionAuditLogs: List<CompanionAdminAuditLog> = CompanionMockData.initialAuditLogs,
    val companionSafetyReports: List<CompanionSafetyReport> = CompanionMockData.initialSafetyReports,
    val activeCompanionSession: CompanionSession? = null,
    val userKycInfo: KycVerificationInfo? = null,
    // Remote UI Config & Admin UI Builder States
    val uiConfig: AppUiConfig = AppUiConfig(),
    val configVersionHistory: List<ConfigVersionRecord> = emptyList(),
    val apkBuilds: List<ApkBuildRecord> = emptyList(),
    val isRefreshingConfig: Boolean = false
)

class FriendTalkViewModel : ViewModel() {

    private val companionRepository: CompanionRepository = InMemoryCompanionRepository()
    private val companionSessionManager = CompanionSessionManager()
    private val remoteUiConfigManager = RemoteUiConfigManager.getInstance()

    private val _uiState = MutableStateFlow(FriendTalkUiState())
    val uiState: StateFlow<FriendTalkUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            remoteUiConfigManager.activeConfig.collect { config ->
                _uiState.value = _uiState.value.copy(uiConfig = config)
            }
        }
        viewModelScope.launch {
            remoteUiConfigManager.versionHistory.collect { history ->
                _uiState.value = _uiState.value.copy(configVersionHistory = history)
            }
        }
        viewModelScope.launch {
            remoteUiConfigManager.apkBuilds.collect { builds ->
                _uiState.value = _uiState.value.copy(apkBuilds = builds)
            }
        }
        viewModelScope.launch {
            remoteUiConfigManager.isRefreshing.collect { refreshing ->
                _uiState.value = _uiState.value.copy(isRefreshingConfig = refreshing)
            }
        }
        viewModelScope.launch {
            companionRepository.profiles.collect { list ->
                _uiState.value = _uiState.value.copy(companionProfiles = list)
            }
        }
        viewModelScope.launch {
            companionRepository.pricingConfig.collect { pricing ->
                _uiState.value = _uiState.value.copy(companionPricingConfig = pricing)
            }
        }
        viewModelScope.launch {
            companionRepository.wallets.collect { wallets ->
                _uiState.value = _uiState.value.copy(companionWallets = wallets)
            }
        }
        viewModelScope.launch {
            companionRepository.giftRequests.collect { reqs ->
                _uiState.value = _uiState.value.copy(companionGiftRequests = reqs)
            }
        }
        viewModelScope.launch {
            companionRepository.auditLogs.collect { logs ->
                _uiState.value = _uiState.value.copy(companionAuditLogs = logs)
            }
        }
        viewModelScope.launch {
            companionRepository.safetyReports.collect { reports ->
                _uiState.value = _uiState.value.copy(companionSafetyReports = reports)
            }
        }
    }

    fun setActiveTab(tab: String) {
        if (tab == "companion" && _uiState.value.currentUser.age < 18) {
            return
        }
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setSelectedFeedCategory(category: String) {
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(selectedFeedCategory = category)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isSearchActive = active, searchQuery = if (!active) "" else _uiState.value.searchQuery)
    }

    fun setCurrentLiveRoomId(id: String?) {
        _uiState.value = _uiState.value.copy(currentLiveRoomId = id)
    }

    fun setActiveChatRoomId(id: String?) {
        _uiState.value = _uiState.value.copy(activeChatRoomId = id)
    }

    // --- User & Profile ---
    fun syncFirebaseUser(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        val mappedUser = com.example.service.AuthService.getInstance().mapFirebaseUserToFriendTalkUser(firebaseUser)
        val existingIndex = _uiState.value.users.indexOfFirst {
            it.id == firebaseUser.uid
        }

        val updatedUser = if (existingIndex >= 0) {
            val existing = _uiState.value.users[existingIndex]
            mappedUser.copy(
                role = existing.role,
                coins = existing.coins,
                diamonds = existing.diamonds,
                isBanned = existing.isBanned,
                banReason = existing.banReason,
                backendRoleStatus = existing.backendRoleStatus.ifBlank { mappedUser.backendRoleStatus }
            )
        } else {
            mappedUser
        }

        val updatedUsers = if (existingIndex >= 0) {
            _uiState.value.users.mapIndexed { index, u -> if (index == existingIndex) updatedUser else u }
        } else {
            listOf(updatedUser) + _uiState.value.users
        }
        _uiState.value = _uiState.value.copy(
            currentUser = updatedUser,
            users = updatedUsers
        )

        // ตรวจสอบสิทธิ์ผ่าน Backend จริง (Requirement 6 & 7)
        com.example.service.AuthService.getInstance().checkBackendSuperAdminRole(firebaseUser.uid) { isSuperAdmin, statusMessage ->
            if (isSuperAdmin) {
                val superAdminUser = _uiState.value.currentUser.copy(
                    role = UserRole.SUPERADMIN,
                    backendRoleStatus = statusMessage,
                    badges = listOf("👑 SUPER ADMIN (Backend Verified)", "ยืนยันตัวตนแล้ว 🛡️")
                )
                _uiState.value = _uiState.value.copy(
                    currentUser = superAdminUser,
                    users = _uiState.value.users.map { if (it.id == firebaseUser.uid) superAdminUser else it }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    currentUser = _uiState.value.currentUser.copy(backendRoleStatus = statusMessage)
                )
            }
        }
    }

    // --- Admin & Roles Management (RBAC) ---
    fun updateUserRole(targetUserId: String, newRole: UserRole): Boolean {
        val currentAdmin = _uiState.value.currentUser
        // เฉพาะ SUPERADMIN เท่านั้นที่สามารถเปลี่ยนบทบาทผู้ใช้ได้
        if (currentAdmin.role != UserRole.SUPERADMIN) {
            return false
        }
        val targetUser = _uiState.value.users.firstOrNull { it.id == targetUserId } ?: return false

        // ป้องกันบัญชี SUPER ADMIN (เจ้าของแอป) ไม่ให้ถูกแก้ไขหรือลดระดับ
        if (targetUser.email.equals("mama.ff9522@gmail.com", ignoreCase = true) ||
            targetUser.username.equals("mama", ignoreCase = true)) {
            return false
        }

        val updatedUsers = _uiState.value.users.map { u ->
            if (u.id == targetUserId) u.copy(role = newRole) else u
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "เปลี่ยนระดับสิทธิ์ (Role Changed)",
            details = "เปลี่ยนสิทธิ์ของ ${targetUser.displayName} เป็น $newRole",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            users = updatedUsers,
            currentUser = if (_uiState.value.currentUser.id == targetUserId) _uiState.value.currentUser.copy(role = newRole) else _uiState.value.currentUser,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun banUser(targetUserId: String, reason: String): Boolean {
        val currentAdmin = _uiState.value.currentUser
        if (currentAdmin.role != UserRole.SUPERADMIN && currentAdmin.role != UserRole.ADMIN) {
            return false
        }
        val targetUser = _uiState.value.users.firstOrNull { it.id == targetUserId } ?: return false

        // บัญชี SUPER ADMIN ห้ามถูกแบนเด็ดขาด
        if (targetUser.role == UserRole.SUPERADMIN ||
            targetUser.email.equals("mama.ff9522@gmail.com", ignoreCase = true)) {
            return false
        }

        val effectiveReason = reason.ifBlank { "ละเมิดข้อกำหนดการใช้งานชุมชน" }
        val updatedUsers = _uiState.value.users.map { u ->
            if (u.id == targetUserId) u.copy(isBanned = true, banReason = effectiveReason) else u
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "ระงับการใช้งาน (Ban User)",
            details = "ระงับบัญชี ${targetUser.displayName} เหตุผล: $effectiveReason",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            users = updatedUsers,
            currentUser = if (_uiState.value.currentUser.id == targetUserId) _uiState.value.currentUser.copy(isBanned = true, banReason = effectiveReason) else _uiState.value.currentUser,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun unbanUser(targetUserId: String): Boolean {
        val currentAdmin = _uiState.value.currentUser
        if (currentAdmin.role != UserRole.SUPERADMIN && currentAdmin.role != UserRole.ADMIN) {
            return false
        }
        val targetUser = _uiState.value.users.firstOrNull { it.id == targetUserId } ?: return false

        val updatedUsers = _uiState.value.users.map { u ->
            if (u.id == targetUserId) u.copy(isBanned = false, banReason = "") else u
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "ปลดระงับการใช้งาน (Unban User)",
            details = "ปลดระงับบัญชี ${targetUser.displayName}",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            users = updatedUsers,
            currentUser = if (_uiState.value.currentUser.id == targetUserId) _uiState.value.currentUser.copy(isBanned = false, banReason = "") else _uiState.value.currentUser,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun adjustUserWallet(targetUserId: String, deltaCoins: Int, deltaDiamonds: Int): Boolean {
        val currentAdmin = _uiState.value.currentUser
        // เฉพาะ SUPERADMIN เท่านั้นที่สามารถเพิ่ม/ลดเหรียญเพชรได้โดยตรง
        if (currentAdmin.role != UserRole.SUPERADMIN) {
            return false
        }
        val targetUser = _uiState.value.users.firstOrNull { it.id == targetUserId } ?: return false

        val updatedUsers = _uiState.value.users.map { u ->
            if (u.id == targetUserId) {
                u.copy(
                    coins = (u.coins + deltaCoins).coerceAtLeast(0),
                    diamonds = (u.diamonds + deltaDiamonds).coerceAtLeast(0)
                )
            } else u
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "ปรับยอดเงินกระเป๋า (Wallet Adjustment)",
            details = "ปรับเหรียญ (${if (deltaCoins >= 0) "+$deltaCoins" else "$deltaCoins"}) และเพชร (${if (deltaDiamonds >= 0) "+$deltaDiamonds" else "$deltaDiamonds"}) ให้แก่ ${targetUser.displayName}",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            users = updatedUsers,
            currentUser = if (_uiState.value.currentUser.id == targetUserId) {
                _uiState.value.currentUser.copy(
                    coins = (_uiState.value.currentUser.coins + deltaCoins).coerceAtLeast(0),
                    diamonds = (_uiState.value.currentUser.diamonds + deltaDiamonds).coerceAtLeast(0)
                )
            } else _uiState.value.currentUser,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun updateReportStatus(reportId: String, newStatus: ReportStatus): Boolean {
        val currentAdmin = _uiState.value.currentUser
        if (currentAdmin.role != UserRole.SUPERADMIN && currentAdmin.role != UserRole.ADMIN && currentAdmin.role != UserRole.MODERATOR) {
            return false
        }
        val rep = _uiState.value.reports.firstOrNull { it.id == reportId } ?: return false

        val updatedReports = _uiState.value.reports.map { r ->
            if (r.id == reportId) r.copy(status = newStatus) else r
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "อัปเดตสถานะรายงาน (Report Status)",
            details = "เปลี่ยนสถานะรายงาน #${reportId.takeLast(6)} (${rep.targetName}) เป็น $newStatus",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            reports = updatedReports,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun approveWithdrawal(withdrawalId: String): Boolean {
        val currentAdmin = _uiState.value.currentUser
        if (currentAdmin.role != UserRole.SUPERADMIN && currentAdmin.role != UserRole.ADMIN) {
            return false
        }
        val wd = _uiState.value.withdrawals.firstOrNull { it.id == withdrawalId } ?: return false

        val updatedWds = _uiState.value.withdrawals.map { w ->
            if (w.id == withdrawalId) w.copy(status = "APPROVED") else w
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "อนุมัติการถอนเงิน (Withdrawal Approved)",
            details = "อนุมัติการถอนเงิน ${wd.amountBaht} บาท (${wd.amountDiamonds} เพชร) ของ ${wd.userName} (${wd.bankName})",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            withdrawals = updatedWds,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun rejectWithdrawal(withdrawalId: String, reason: String): Boolean {
        val currentAdmin = _uiState.value.currentUser
        if (currentAdmin.role != UserRole.SUPERADMIN && currentAdmin.role != UserRole.ADMIN) {
            return false
        }
        val wd = _uiState.value.withdrawals.firstOrNull { it.id == withdrawalId } ?: return false

        val effectiveReason = reason.ifBlank { "ข้อมูลบัญชีไม่ถูกต้อง หรือไม่ผ่านเกณฑ์การตรวจสอบ" }
        val updatedWds = _uiState.value.withdrawals.map { w ->
            if (w.id == withdrawalId) w.copy(status = "REJECTED") else w
        }
        val log = AdminLog(
            id = "log_${System.currentTimeMillis()}",
            adminName = currentAdmin.displayName,
            action = "ปฏิเสธการถอนเงิน (Withdrawal Rejected)",
            details = "ปฏิเสธคำขอถอนเงิน ${wd.amountBaht} บาท ของ ${wd.userName} เหตุผล: $effectiveReason",
            timestamp = "ขณะนี้"
        )
        _uiState.value = _uiState.value.copy(
            withdrawals = updatedWds,
            adminLogs = listOf(log) + _uiState.value.adminLogs
        )
        SoundService.playPop()
        return true
    }

    fun switchUser(userId: String) {
        // ระบบสลับบัญชีจำลองถูกลบแล้ว - บังคับใช้ Firebase Authentication เท่านั้น
    }

    fun registerUser(displayName: String, username: String, age: Int, gender: Gender) {
        // ระบบสร้างบัญชีจำลองถูกลบแล้ว - บังคับใช้ Firebase Authentication และ Google Sign-In เท่านั้น
    }

    fun updateProfile(displayName: String, bio: String, age: Int, gender: Gender, province: String, interests: List<String>) {
        val updatedUser = _uiState.value.currentUser.copy(
            displayName = displayName,
            bio = bio,
            age = age,
            gender = gender,
            location = _uiState.value.currentUser.location.copy(province = province),
            interests = interests
        )
        _uiState.value = _uiState.value.copy(
            currentUser = updatedUser,
            users = _uiState.value.users.map { if (it.id == updatedUser.id) updatedUser else it }
        )
    }

    fun toggleLocationSharing() {
        val currentLoc = _uiState.value.currentUser.location
        val updatedUser = _uiState.value.currentUser.copy(
            location = currentLoc.copy(isSharingLocation = !currentLoc.isSharingLocation)
        )
        _uiState.value = _uiState.value.copy(
            currentUser = updatedUser,
            users = _uiState.value.users.map { if (it.id == updatedUser.id) updatedUser else it }
        )
    }

    // --- Feed & Posts ---
    fun createPost(content: String, type: PostType, images: List<String>, videoUrl: String? = null, tags: List<String> = emptyList()) {
        val newPost = Post(
            id = "post_${System.currentTimeMillis()}",
            authorId = _uiState.value.currentUser.id,
            authorName = _uiState.value.currentUser.displayName,
            authorAvatar = _uiState.value.currentUser.avatar,
            authorUsername = _uiState.value.currentUser.username,
            authorIsVerified = _uiState.value.currentUser.isVerified,
            authorBadges = _uiState.value.currentUser.badges,
            location = _uiState.value.currentUser.location.province,
            timestamp = "เมื่อสักครู่",
            type = type,
            content = content,
            images = images,
            videoUrl = videoUrl,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            tags = tags
        )
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(posts = listOf(newPost) + _uiState.value.posts)
    }

    fun toggleLikePost(postId: String) {
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == postId) {
                    val isLiked = !post.isLiked
                    val newCount = if (isLiked) post.likesCount + 1 else maxOf(0, post.likesCount - 1)
                    post.copy(isLiked = isLiked, likesCount = newCount)
                } else post
            }
        )
    }

    fun toggleBookmarkPost(postId: String) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == postId) post.copy(isBookmarked = !post.isBookmarked) else post
            }
        )
    }

    fun deletePost(postId: String) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.filter { it.id != postId }
        )
    }

    fun addComment(postId: String, text: String) {
        if (text.isBlank()) return
        val newComment = PostComment(
            id = "c_${System.currentTimeMillis()}",
            postId = postId,
            userId = _uiState.value.currentUser.id,
            userDisplayName = _uiState.value.currentUser.displayName,
            userAvatar = _uiState.value.currentUser.avatar,
            content = text,
            timestamp = "เมื่อสักครู่"
        )
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == postId) {
                    post.copy(
                        comments = post.comments + newComment,
                        commentsCount = post.commentsCount + 1
                    )
                } else post
            }
        )
    }

    fun sharePost(postId: String) {
        val target = _uiState.value.posts.find { it.id == postId } ?: return
        val sharedPost = Post(
            id = "post_${System.currentTimeMillis()}",
            authorId = _uiState.value.currentUser.id,
            authorName = _uiState.value.currentUser.displayName,
            authorAvatar = _uiState.value.currentUser.avatar,
            authorUsername = _uiState.value.currentUser.username,
            authorIsVerified = _uiState.value.currentUser.isVerified,
            location = _uiState.value.currentUser.location.province,
            timestamp = "เมื่อสักครู่",
            type = PostType.SHARED_POST,
            content = "แชร์โพสต์น่าสนใจจาก ${target.authorName} ✨",
            sharedPost = SharedPostContent(
                originalPostId = target.id,
                originalAuthorName = target.authorName,
                originalAuthorAvatar = target.authorAvatar,
                originalContent = target.content,
                originalImageUrl = target.images.firstOrNull(),
                originalTimestamp = target.timestamp
            ),
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0
        )
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(
            posts = listOf(sharedPost) + _uiState.value.posts.map {
                if (it.id == postId) it.copy(sharesCount = it.sharesCount + 1) else it
            }
        )
    }

    // --- Story ---
    fun addStory(imageUrl: String) {
        val newStory = Story(
            id = "story_${System.currentTimeMillis()}",
            userId = _uiState.value.currentUser.id,
            userName = _uiState.value.currentUser.displayName,
            userAvatar = _uiState.value.currentUser.avatar,
            imageUrl = imageUrl,
            timestamp = "เมื่อสักครู่"
        )
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(stories = listOf(newStory) + _uiState.value.stories)
    }

    // --- Live & PK ---
    fun sendLiveComment(roomId: String, text: String) {
        if (text.isBlank()) return
        val newComment = LiveComment(
            id = "lc_${System.currentTimeMillis()}",
            userId = _uiState.value.currentUser.id,
            userName = _uiState.value.currentUser.displayName,
            userAvatar = _uiState.value.currentUser.avatar,
            text = text
        )
        _uiState.value = _uiState.value.copy(
            liveRooms = _uiState.value.liveRooms.map { room ->
                if (room.id == roomId) {
                    room.copy(recentComments = room.recentComments + newComment)
                } else room
            }
        )
    }

    fun sendLiveGift(roomId: String, gift: GiftItem) {
        if (_uiState.value.currentUser.coins < gift.coins) return

        val updatedUser = _uiState.value.currentUser.copy(coins = _uiState.value.currentUser.coins - gift.coins)
        val giftComment = LiveComment(
            id = "lc_${System.currentTimeMillis()}",
            userId = updatedUser.id,
            userName = updatedUser.displayName,
            userAvatar = updatedUser.avatar,
            text = "ส่ง ${gift.name} ${gift.icon}",
            isGift = true,
            giftIcon = gift.icon
        )

        SoundService.playGift()
        _uiState.value = _uiState.value.copy(
            currentUser = updatedUser,
            activeGiftAnimation = ActiveGiftAnimation("anim_${System.currentTimeMillis()}", gift, updatedUser.displayName, updatedUser.avatar),
            liveRooms = _uiState.value.liveRooms.map { room ->
                if (room.id == roomId) {
                    val currentPk = room.pkState
                    val newPk = if (currentPk.isPkActive) currentPk.copy(myScore = currentPk.myScore + gift.coins) else currentPk
                    room.copy(
                        diamondsEarned = room.diamondsEarned + (gift.coins / 2),
                        likesCount = room.likesCount + gift.coins,
                        recentComments = room.recentComments + giftComment,
                        pkState = newPk
                    )
                } else room
            }
        )

        viewModelScope.launch {
            delay(2500)
            _uiState.value = _uiState.value.copy(activeGiftAnimation = null)
        }
    }

    // --- Chat & Messages ---
    fun sendChatMessage(conversationId: String, text: String) {
        if (text.isBlank()) return
        val newMsg = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderId = _uiState.value.currentUser.id,
            senderName = _uiState.value.currentUser.displayName,
            senderAvatar = _uiState.value.currentUser.avatar,
            conversationId = conversationId,
            text = text,
            timestamp = "เมื่อสักครู่"
        )
        SoundService.playPop()
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + newMsg,
            conversations = _uiState.value.conversations.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(lastMessage = text, lastMessageTimestamp = "เมื่อสักครู่")
                } else conv
            }
        )
    }

    fun startCall(partnerId: String, partnerName: String, partnerAvatar: String, type: CallType) {
        SoundService.playCall()
        val session = CallSession(
            id = "call_${System.currentTimeMillis()}",
            callerId = _uiState.value.currentUser.id,
            receiverId = partnerId,
            partnerName = partnerName,
            partnerAvatar = partnerAvatar,
            type = type,
            status = CallStatus.CONNECTED
        )
        _uiState.value = _uiState.value.copy(activeCall = session)
    }

    fun endCall() {
        _uiState.value = _uiState.value.copy(activeCall = null)
    }

    // --- Wallet & Coins ---
    fun buyCoins(coinsAmount: Int, bahtPrice: Double) {
        val updatedUser = _uiState.value.currentUser.copy(coins = _uiState.value.currentUser.coins + coinsAmount)
        SoundService.playGift()
        _uiState.value = _uiState.value.copy(
            currentUser = updatedUser,
            users = _uiState.value.users.map { if (it.id == updatedUser.id) updatedUser else it }
        )
    }

    // --- Companion 18+ Actions ---
    fun applyAsCompanion(profile: CompanionProfile) {
        companionRepository.applyAsCompanion(profile)
    }

    fun approveCompanion(companionId: String, reason: String) {
        companionRepository.adminApproveCompanion(companionId, _uiState.value.currentUser.id, _uiState.value.currentUser.displayName, reason)
    }

    fun rejectCompanion(companionId: String, reason: String) {
        companionRepository.adminRejectCompanion(companionId, _uiState.value.currentUser.id, _uiState.value.currentUser.displayName, reason)
    }

    fun suspendCompanion(companionId: String, reason: String) {
        companionRepository.adminSuspendCompanion(companionId, _uiState.value.currentUser.id, _uiState.value.currentUser.displayName, reason)
    }

    fun updateCompanionPricing(level: CompanionLevel, text: Int, voice: Int, video: Int, reason: String) {
        companionRepository.updatePricingRates(level, text, voice, video, _uiState.value.currentUser.id, _uiState.value.currentUser.displayName, reason)
    }

    fun startCompanionSession(companion: CompanionProfile, serviceType: CompanionServiceType, rate: Int) {
        val session = companionSessionManager.createSession(companion, _uiState.value.currentUser.id, _uiState.value.currentUser.displayName, serviceType, rate)
        _uiState.value = _uiState.value.copy(activeCompanionSession = session)
    }

    fun endCompanionSession(durationSec: Int) {
        val current = _uiState.value.activeCompanionSession ?: return
        val ended = companionSessionManager.endSession(current, durationSec)
        companionRepository.creditSessionEarning(ended.companionId, ended, ended.companionCoinsEarned)
        _uiState.value = _uiState.value.copy(activeCompanionSession = null)
    }

    fun sendCompanionGiftRequest(companionId: String, gift: GiftItem, note: String) {
        val comp = _uiState.value.companionProfiles.find { it.id == companionId } ?: return
        val req = CompanionGiftRequest(
            id = "greq_${System.currentTimeMillis()}",
            senderId = _uiState.value.currentUser.id,
            senderName = _uiState.value.currentUser.displayName,
            senderAvatar = _uiState.value.currentUser.avatar,
            receiverId = comp.userId,
            receiverName = comp.displayName,
            gift = gift,
            note = note
        )
        companionRepository.sendGiftRequest(req)
    }

    fun respondGiftRequest(requestId: String, accept: Boolean) {
        companionRepository.respondGiftRequest(requestId, accept)
    }

    fun requestCompanionWithdrawal(companionId: String, amountCoins: Int, bankAccount: CompanionBankAccount): Boolean {
        return companionRepository.requestWithdrawal(companionId, _uiState.value.currentUser.displayName, amountCoins, bankAccount)
    }

    fun submitSafetyReport(companionId: String, companionName: String, category: String, text: String) {
        val rep = CompanionSafetyReport(
            id = "rep_${System.currentTimeMillis()}",
            reporterId = _uiState.value.currentUser.id,
            reporterName = _uiState.value.currentUser.displayName,
            companionId = companionId,
            companionName = companionName,
            category = category,
            evidenceText = text,
            timestamp = "วันนี้"
        )
        companionRepository.submitSafetyReport(rep)
    }

    fun submitKyc(info: KycVerificationInfo) {
        _uiState.value = _uiState.value.copy(userKycInfo = info.copy(status = KycStatus.VERIFIED))
    }

    // --- Remote UI Config Manager & Admin Builder Actions ---
    fun startEditingUiConfig(): AppUiConfig {
        return remoteUiConfigManager.startEditing()
    }

    fun updateDraftUiConfig(config: AppUiConfig) {
        remoteUiConfigManager.updateDraft(config)
    }

    fun publishUiConfig(config: AppUiConfig, notes: String): String {
        return remoteUiConfigManager.publish(config, _uiState.value.currentUser.displayName, notes)
    }

    fun rollbackUiConfig(version: String): Boolean {
        return remoteUiConfigManager.rollbackTo(version, _uiState.value.currentUser.displayName)
    }

    fun restoreDefaultUiConfig() {
        remoteUiConfigManager.restoreDefault(_uiState.value.currentUser.displayName)
    }

    fun refreshRemoteUiConfig() {
        viewModelScope.launch {
            remoteUiConfigManager.refreshRemoteConfig()
        }
    }

    fun submitReport(report: Report) {
        _uiState.value = _uiState.value.copy(reports = listOf(report) + _uiState.value.reports)
    }

    fun markNotificationRead(notifId: String) {
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map {
                if (it.id == notifId) it.copy(isRead = true) else it
            }
        )
    }
}
