package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.companion.ui.*
import com.example.model.parseHexColor
import com.example.service.AuthService
import com.example.ui.admin.AdminDashboardDialog
import com.example.ui.admin.AdminUiBuilderDialog
import com.example.ui.admin.ApkDownloadDialog
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.CallDialog
import com.example.ui.chat.ChatRoomDialog
import com.example.ui.chat.ChatTab
import com.example.ui.chat.CreateGroupDialog
import com.example.ui.components.*
import com.example.ui.discover.DiscoverTab
import com.example.ui.feed.FeedTab
import com.example.ui.live.LiveRoomDialog
import com.example.ui.live.LiveTab
import com.example.ui.live.TopGifterDialog
import com.example.ui.movie.MovieTab
import com.example.ui.profile.CreatorStudioDialog
import com.example.ui.profile.EditProfileDialog
import com.example.ui.profile.ProfileTab
import com.example.ui.theme.*
import com.example.viewmodel.FriendTalkViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            AuthService.getInstance().initialize(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "Firebase initialization on startup: ${e.message}")
        }
        enableEdgeToEdge()
        setContent {
            FriendTalkRoot()
        }
    }
}

@Composable
fun FriendTalkRoot(
    viewModel: FriendTalkViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { AuthService.getInstance() }
    val firebaseUser by authService.currentUser.collectAsStateWithLifecycle()
    val isInitialized by authService.isInitialized.collectAsStateWithLifecycle()

    // Attempt auto sign-in once on startup
    LaunchedEffect(Unit) {
        authService.attemptAutoSignIn(
            context = context,
            scope = coroutineScope,
            onSuccess = { user ->
                viewModel.syncFirebaseUser(user)
            },
            onUnauthenticated = {
                // Stay on AuthScreen
            }
        )
    }

    LaunchedEffect(firebaseUser) {
        Log.i("MainActivity", "[AUTH_FLOW_DEBUG_STEP_8_NAV] LaunchedEffect(firebaseUser) observed: uid=${firebaseUser?.uid ?: "null"}")
        firebaseUser?.let { user ->
            viewModel.syncFirebaseUser(user)
        }
    }

    FriendTalkTheme {
        if (!isInitialized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate950),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Pink500,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (firebaseUser == null) {
            AuthScreen(
                onAuthSuccess = { user ->
                    viewModel.syncFirebaseUser(user)
                }
            )
        } else {
            FriendTalkApp(
                viewModel = viewModel,
                currentUserFirebase = firebaseUser,
                onSignOut = {
                    authService.signOut(context, coroutineScope) {}
                }
            )
        }
    }
}

@Composable
fun FriendTalkApp(
    viewModel: FriendTalkViewModel,
    currentUserFirebase: FirebaseUser?,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isCoinsOpen by remember { mutableStateOf(false) }
    var isNotifsOpen by remember { mutableStateOf(false) }
    var isAuthOpen by remember { mutableStateOf(false) }
    var isPermissionsOpen by remember { mutableStateOf(false) }
    var isAdminDashboardOpen by remember { mutableStateOf(false) }
    var isUiBuilderOpen by remember { mutableStateOf(false) }
    var isApkDownloadOpen by remember { mutableStateOf(false) }
    var isEditProfileOpen by remember { mutableStateOf(false) }
    var isCreatorStudioOpen by remember { mutableStateOf(false) }
    var isCreateGroupOpen by remember { mutableStateOf(false) }
    var isTopGiftersOpen by remember { mutableStateOf(false) }
    var isCompanionApplyOpen by remember { mutableStateOf(false) }
    var isCompanionKycOpen by remember { mutableStateOf(false) }
    var isCompanionAdminOpen by remember { mutableStateOf(false) }
    var activeCompanionWalletId by remember { mutableStateOf<String?>(null) }

    val unreadNotificationsCount = uiState.notifications.count { !it.isRead }
    val totalUnreadMessages = uiState.conversations.sumOf { it.unreadCounts[uiState.currentUser.id] ?: 0 }
    val activeLiveCount = uiState.liveRooms.count { it.isLive }

    val appBgColor = parseHexColor(uiState.uiConfig.theme.backgroundColorHex, Slate950)

    FriendTalkTheme(themeConfig = uiState.uiConfig.theme) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(appBgColor),
            topBar = {
                if (uiState.uiConfig.feed.showHeader) {
                    AppHeader(
                        currentUser = uiState.currentUser,
                        unreadNotificationsCount = unreadNotificationsCount,
                        searchQuery = uiState.searchQuery,
                        isSearchActive = uiState.isSearchActive,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onToggleSearch = { viewModel.toggleSearchActive(it) },
                        onOpenCoins = { isCoinsOpen = true },
                        onOpenNotifications = { isNotifsOpen = true },
                        onOpenAuth = { isAuthOpen = true },
                        onOpenPermissions = { isPermissionsOpen = true },
                        onToggleLocationSharing = { viewModel.toggleLocationSharing() },
                        uiConfig = uiState.uiConfig,
                        onRefreshConfig = { viewModel.refreshRemoteUiConfig() },
                        isRefreshing = uiState.isRefreshingConfig,
                        onOpenAdminDashboard = { isAdminDashboardOpen = true },
                        onOpenUiBuilder = { isUiBuilderOpen = true },
                        onOpenApkDownload = { isApkDownloadOpen = true }
                    )
                }
            },
            bottomBar = {
                BottomNavBar(
                    activeTab = uiState.activeTab,
                    onTabSelected = { viewModel.setActiveTab(it) },
                    totalUnreadMessages = totalUnreadMessages,
                    activeLiveCount = activeLiveCount,
                    currentUserAge = uiState.currentUser.age,
                    uiConfig = uiState.uiConfig
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(appBgColor)
            ) {
                when (uiState.activeTab) {
                    "home" -> FeedTab(
                        posts = uiState.posts,
                        stories = uiState.stories,
                        users = uiState.users,
                        currentUser = uiState.currentUser,
                        clubs = uiState.clubs,
                        uiConfig = uiState.uiConfig,
                        selectedCategory = uiState.selectedFeedCategory,
                        searchQuery = uiState.searchQuery,
                        onSelectCategory = { viewModel.setSelectedFeedCategory(it) },
                        onLikePost = { viewModel.toggleLikePost(it) },
                        onBookmarkPost = { viewModel.toggleBookmarkPost(it) },
                        onDeletePost = { viewModel.deletePost(it) },
                        onSharePost = { viewModel.sharePost(it) },
                        onAddComment = { postId, text -> viewModel.addComment(postId, text) },
                        onCreatePost = { content, type, images, videoUrl, tags ->
                            viewModel.createPost(content, type, images, videoUrl, tags)
                        },
                        onAddStory = { viewModel.addStory(it) },
                        onEnterLiveRoom = { viewModel.setCurrentLiveRoomId(it) },
                        onSubmitReport = { viewModel.submitReport(it) }
                    )

                    "discover" -> DiscoverTab(
                        users = uiState.users,
                        currentUser = uiState.currentUser,
                        onStartChat = { user ->
                            val conv = uiState.conversations.find { it.participantIds.contains(user.id) }
                            if (conv != null) {
                                viewModel.setActiveChatRoomId(conv.id)
                            } else {
                                viewModel.setActiveTab("chat")
                            }
                        }
                    )

                    "live" -> LiveTab(
                        liveRooms = uiState.liveRooms,
                        currentUser = uiState.currentUser,
                        onSelectRoom = { viewModel.setCurrentLiveRoomId(it) },
                        onGoLive = { /* Go Live */ }
                    )

                    "movie" -> MovieTab()

                    "chat" -> ChatTab(
                        conversations = uiState.conversations,
                        users = uiState.users,
                        currentUser = uiState.currentUser,
                        onSelectConversation = { viewModel.setActiveChatRoomId(it) },
                        onCreateGroup = { isCreateGroupOpen = true }
                    )

                    "companion" -> CompanionMainScreen(
                        currentUser = uiState.currentUser,
                        profiles = uiState.companionProfiles,
                        wallets = uiState.companionWallets,
                        pricingConfig = uiState.companionPricingConfig,
                        onOpenApply = { isCompanionApplyOpen = true },
                        onOpenKyc = { isCompanionKycOpen = true },
                        onOpenAdmin = { isCompanionAdminOpen = true },
                        onOpenWallet = { activeCompanionWalletId = it },
                        onStartSession = { comp, serviceType, rate ->
                            viewModel.startCompanionSession(comp, serviceType, rate)
                        },
                        onSendGiftRequest = { compId, gift, note ->
                            viewModel.sendCompanionGiftRequest(compId, gift, note)
                        },
                        onReportSafety = { compId, name ->
                            viewModel.submitSafetyReport(compId, name, "INAPPROPRIATE", "รายงานความปลอดภัย")
                        }
                    )

                    "profile" -> ProfileTab(
                        currentUser = uiState.currentUser,
                        onEditProfile = { isEditProfileOpen = true },
                        onOpenCreatorStudio = { isCreatorStudioOpen = true },
                        onOpenCoins = { isCoinsOpen = true },
                        onOpenPermissions = { isPermissionsOpen = true },
                        onOpenCompanionApply = { isCompanionApplyOpen = true }
                    )
                }

                // Global Gift Animation Overlay
                GiftAnimationOverlay(activeGift = uiState.activeGiftAnimation)
            }
        }
    }

    // Live Room Dialog
    uiState.currentLiveRoomId?.let { roomId ->
        val room = uiState.liveRooms.find { it.id == roomId }
        if (room != null) {
            LiveRoomDialog(
                room = room,
                currentUser = uiState.currentUser,
                onDismiss = { viewModel.setCurrentLiveRoomId(null) },
                onSendComment = { text -> viewModel.sendLiveComment(room.id, text) },
                onSendGift = { gift -> viewModel.sendLiveGift(room.id, gift) },
                onOpenTopGifters = { isTopGiftersOpen = true }
            )
        }
    }

    // Chat Room Dialog
    uiState.activeChatRoomId?.let { convId ->
        val conv = uiState.conversations.find { it.id == convId }
        if (conv != null) {
            val msgs = uiState.messages.filter { it.conversationId == convId }
            ChatRoomDialog(
                conversation = conv,
                messages = msgs,
                currentUser = uiState.currentUser,
                users = uiState.users,
                onDismiss = { viewModel.setActiveChatRoomId(null) },
                onSendMessage = { text -> viewModel.sendChatMessage(conv.id, text) },
                onStartCall = { type ->
                    val otherUserId = conv.participantIds.firstOrNull { it != uiState.currentUser.id }
                    val otherUser = uiState.users.find { it.id == otherUserId }
                    if (otherUser != null) {
                        viewModel.startCall(otherUser.id, otherUser.displayName, otherUser.avatar, type)
                    }
                }
            )
        }
    }

    // Call Dialog
    uiState.activeCall?.let { call ->
        CallDialog(
            callSession = call,
            onEndCall = { viewModel.endCall() }
        )
    }

    // Active Companion Session Dialog
    uiState.activeCompanionSession?.let { sess ->
        CompanionSessionActiveDialog(
            session = sess,
            onEndSession = { duration ->
                viewModel.endCompanionSession(duration)
            }
        )
    }

    // App Dialogs
    if (isCoinsOpen) {
        CoinWalletDialog(
            currentUser = uiState.currentUser,
            onDismiss = { isCoinsOpen = false },
            onBuyCoins = { coins, baht -> viewModel.buyCoins(coins, baht) }
        )
    }

    if (isNotifsOpen) {
        NotificationDialog(
            notifications = uiState.notifications,
            onDismiss = { isNotifsOpen = false },
            onMarkAsRead = { viewModel.markNotificationRead(it) }
        )
    }

    if (isAuthOpen) {
        AuthDialog(
            currentUser = uiState.currentUser,
            users = uiState.users,
            onDismiss = { isAuthOpen = false },
            onSwitchUser = { viewModel.switchUser(it) },
            onRegisterUser = { dName, uName, age, gen ->
                viewModel.registerUser(dName, uName, age, gen)
            }
        )
    }

    if (isPermissionsOpen) {
        PermissionDialog(onDismiss = { isPermissionsOpen = false })
    }

    if (uiState.currentUser.isBanned) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Slate900,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = Rose500,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "บัญชีถูกระงับการใช้งาน 🚫",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "บัญชี ${uiState.currentUser.displayName} ถูกระงับชั่วคราว\nเหตุผล: ${uiState.currentUser.banReason.ifBlank { "ละเมิดข้อกำหนดและนโยบายชุมชน" }}",
                        fontSize = 13.sp,
                        color = Slate300,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ออกจากระบบ", fontWeight = FontWeight.Bold, color = White)
                    }
                }
            }
        }
    }

    if (isAdminDashboardOpen) {
        AdminDashboardDialog(
            currentUser = uiState.currentUser,
            users = uiState.users,
            reports = uiState.reports,
            withdrawals = uiState.withdrawals,
            adminLogs = uiState.adminLogs,
            onUpdateUserRole = { userId, newRole ->
                viewModel.updateUserRole(userId, newRole)
            },
            onBanUser = { userId, reason ->
                viewModel.banUser(userId, reason)
            },
            onUnbanUser = { userId ->
                viewModel.unbanUser(userId)
            },
            onAdjustWallet = { userId, coins, diamonds ->
                viewModel.adjustUserWallet(userId, coins, diamonds)
            },
            onUpdateReportStatus = { repId, status ->
                viewModel.updateReportStatus(repId, status)
            },
            onApproveWithdrawal = { wdId ->
                viewModel.approveWithdrawal(wdId)
            },
            onRejectWithdrawal = { wdId, reason ->
                viewModel.rejectWithdrawal(wdId, reason)
            },
            onDismiss = { isAdminDashboardOpen = false },
            onOpenUiBuilder = { isUiBuilderOpen = true },
            onOpenApkDownload = { isApkDownloadOpen = true },
            onOpenCompanionAdmin = { isCompanionAdminOpen = true }
        )
    }

    if (isUiBuilderOpen) {
        AdminUiBuilderDialog(
            currentConfig = uiState.uiConfig,
            versionHistory = uiState.configVersionHistory,
            onDismiss = { isUiBuilderOpen = false },
            onPublish = { config, notes -> viewModel.publishUiConfig(config, notes) },
            onRollback = { version -> viewModel.rollbackUiConfig(version) },
            onRestoreDefault = { viewModel.restoreDefaultUiConfig() }
        )
    }

    if (isApkDownloadOpen) {
        ApkDownloadDialog(
            apkBuilds = uiState.apkBuilds,
            onDismiss = { isApkDownloadOpen = false }
        )
    }

    if (isEditProfileOpen) {
        EditProfileDialog(
            currentUser = uiState.currentUser,
            onDismiss = { isEditProfileOpen = false },
            onSave = { dName, bio, age, gen, prov, interests ->
                viewModel.updateProfile(dName, bio, age, gen, prov, interests)
            }
        )
    }

    if (isCreatorStudioOpen) {
        CreatorStudioDialog(
            currentUser = uiState.currentUser,
            onDismiss = { isCreatorStudioOpen = false }
        )
    }

    if (isCreateGroupOpen) {
        CreateGroupDialog(
            users = uiState.users,
            currentUser = uiState.currentUser,
            onDismiss = { isCreateGroupOpen = false },
            onCreateGroup = { name, members ->
                // Create Group logic
            }
        )
    }

    if (isTopGiftersOpen) {
        TopGifterDialog(
            gifters = uiState.liveRooms.firstOrNull()?.topGifters ?: emptyList(),
            onDismiss = { isTopGiftersOpen = false }
        )
    }

    if (isCompanionApplyOpen) {
        CompanionApplyDialog(
            currentUser = uiState.currentUser,
            onDismiss = { isCompanionApplyOpen = false },
            onSubmitApplication = { viewModel.applyAsCompanion(it) }
        )
    }

    if (isCompanionKycOpen) {
        CompanionKycDialog(
            currentUser = uiState.currentUser,
            onDismiss = { isCompanionKycOpen = false },
            onSubmitKyc = { viewModel.submitKyc(it) }
        )
    }

    if (isCompanionAdminOpen) {
        CompanionAdminDialog(
            profiles = uiState.companionProfiles,
            onDismiss = { isCompanionAdminOpen = false },
            onApprove = { compId, reason -> viewModel.approveCompanion(compId, reason) },
            onReject = { compId, reason -> viewModel.rejectCompanion(compId, reason) },
            onSuspend = { compId, reason -> viewModel.suspendCompanion(compId, reason) }
        )
    }

    activeCompanionWalletId?.let { compId ->
        val wallet = uiState.companionWallets[compId]
        if (wallet != null) {
            CompanionWalletSheet(
                wallet = wallet,
                onDismiss = { activeCompanionWalletId = null },
                onRequestWithdrawal = { amount, bank ->
                    viewModel.requestCompanionWithdrawal(compId, amount, bank)
                }
            )
        }
    }
}
