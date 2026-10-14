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
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
    /** A tapped push carries chatId → open that chat room. */
    private fun openFromNotification(i: android.content.Intent?) {
        i?.getStringExtra("chatId")?.takeIf { it.isNotBlank() }?.let { com.example.social.SocialRepo.openChat.value = it }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFromNotification(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.ui.components.I18n.load(this)
        try {
            AuthService.getInstance().initialize(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "Firebase initialization on startup: ${e.message}")
        }
        com.example.push.PushRepo.ensureChannel(this)
        openFromNotification(intent)
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
    var explicitLogin by remember { mutableStateOf(context.getSharedPreferences("ft_auth", android.content.Context.MODE_PRIVATE).getBoolean("explicit_login_v1", false)) }

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
            if (com.example.social.SocialRepo.upsertOnSignIn(user)) com.example.ui.social.InterestsPrompt.show.value = true
            com.example.social.SocialRepo.startPresence(context)
            com.example.push.PushRepo.register() // this device gets chat / friend / gift notifications
            viewModel.refreshRemoteUiConfig() // published Remote UI config for everyone
            com.example.service.UiLayoutRepository.refreshHome()
            com.example.ui.components.UiPages.refresh()
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
        } else if (firebaseUser == null || !explicitLogin) {
            AuthScreen(
                onAuthSuccess = { user ->
                    context.getSharedPreferences("ft_auth", android.content.Context.MODE_PRIVATE).edit().putBoolean("explicit_login_v1", true).apply(); explicitLogin = true
                    viewModel.syncFirebaseUser(user)
                }
            )
        } else { com.example.ui.onboarding.OnboardingGate(firebaseUser) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    val prefs = context.getSharedPreferences("friendtalk", 0)
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted && !prefs.getBoolean("askedNotif", false)) { prefs.edit().putBoolean("askedNotif", true).apply(); notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                }
            }
            FriendTalkApp(
                viewModel = viewModel,
                currentUserFirebase = firebaseUser,
                onSignOut = {
                    coroutineScope.launch {
                        // Mark inactive + hide from radar, then sign out (Firebase + Credential Manager)
                        runCatching { com.example.push.PushRepo.unregister() }
                        runCatching { com.example.social.SocialRepo.stopPresence() }
                        runCatching {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { u ->
                                com.google.firebase.database.FirebaseDatabase.getInstance(com.example.social.SocialRepo.DB_URL)
                                    .getReference("users/${u.uid}/lastActive").setValue(System.currentTimeMillis())
                                com.example.network.BrainApi.call(u, "DELETE", "/radar/location")
                            }
                        }
                        com.example.social.SocialRepo.openChat.value = null
                        com.example.ads.AdRoom.open.value = null; com.example.ads.AdRoom.quotaHit.value = null; com.example.ads.AdRoom.limits.value = null
                        context.getSharedPreferences("ft_auth", android.content.Context.MODE_PRIVATE).edit().remove("explicit_login_v1").apply()
                        authService.signOut(context, coroutineScope) {
                            // Restart with a cleared back stack: drops all ViewModel/cached role state and listeners
                            val act = context as? android.app.Activity
                            val i = android.content.Intent(context, MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(i); act?.finish()
                        }
                    }
                }
            )
        } }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // HorizontalPager (tab swiping)
@Composable
fun FriendTalkApp(
    viewModel: FriendTalkViewModel,
    currentUserFirebase: FirebaseUser?,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profileScope = rememberCoroutineScope()
    val profileContext = androidx.compose.ui.platform.LocalContext.current

    var isCoinsOpen by remember { mutableStateOf(false) }
    var globalSearch by remember { mutableStateOf(false) }
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
    // Real unread chats (RTDB userChats + readReceipts), refreshed every minute
    val totalUnreadMessages by com.example.ui.feed.HomeRepo.unreadChats.collectAsState()
    LaunchedEffect(uiState.currentUser.id) { while (true) { com.example.ui.feed.HomeRepo.refreshCounts(); kotlinx.coroutines.delay(60_000) } }
    var isHomeNotifsOpen by remember { mutableStateOf(false) }
    if (isHomeNotifsOpen) com.example.ui.feed.HomeNotificationsDialog { isHomeNotifsOpen = false }
    val activeLiveCount = 0 // live rooms are not real yet (no badge from mock data)

    val appBgColor = parseHexColor(uiState.uiConfig.theme.backgroundColorHex, Slate950)

    com.example.ui.social.ProfileNav.openOwn = { viewModel.setActiveTab("profile") }
    // Home uses the original mockup palette (dark navy + magenta/purple neon); other pages keep the published theme.
    // User's mode from the "ฉัน" page: โหมดมืด = Home look on every page, โหมดปกติ = 'หาเพื่อน' look on every page.
    val modeCtx = LocalContext.current
    remember { com.example.ui.theme.AppThemeMode.load(modeCtx) }
    FriendTalkTheme(themeConfig = com.example.ui.theme.AppThemeMode.themeConfig()) {
        // Maintenance mode from published Remote UI config: blocks normal users, admins still get in.
        LaunchedEffect(uiState.currentUser.id) { com.example.ui.components.AppGates.start(); com.example.social.Wallet.start() }
        // System back: other tab → Home; on Home press twice within 2s to exit. Dialogs/sub-screens handle back themselves first.
        val backCtx = LocalContext.current
        var lastBack by remember { mutableStateOf(0L) }
        androidx.activity.compose.BackHandler(enabled = true) {
            if (uiState.activeTab != "home") viewModel.setActiveTab("home")
            else {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) (backCtx as? android.app.Activity)?.finish()
                else { lastBack = now; android.widget.Toast.makeText(backCtx, com.example.ui.components.tr("กดย้อนกลับอีกครั้งเพื่อออก"), android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        if (com.example.ui.components.AppGates.lock.locked && !com.example.ui.components.AppGates.isStaff) {
            com.example.ui.components.AppLockScreen(uiState.uiConfig.appName); return@FriendTalkTheme
        }
        if (uiState.uiConfig.maintenanceMode && uiState.currentUser.role == com.example.model.UserRole.USER) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(appBgColor).padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Text("🛠 " + uiState.uiConfig.maintenanceMessage.ifBlank { "ระบบกำลังปรับปรุง กรุณากลับมาใหม่ภายหลัง" },
                    color = White, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            return@FriendTalkTheme
        }
        com.example.ui.theme.ModeBackground()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier
                .fillMaxSize(),
            topBar = {
                Column {
                com.example.ui.social.VerifyEmailBanner()
                if (globalSearch) com.example.ui.social.GlobalSearchDialog { globalSearch = false }
                // Same header as Home on every page (owner request). AppHeader / FriendsHeader are kept in code but no longer shown.
                if (uiState.uiConfig.feed.showHeader) {
                    com.example.ui.feed.HomeHeader(
                        currentUser = uiState.currentUser,
                        onAvatar = { if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) viewModel.setActiveTab("profile") else isAuthOpen = true },
                        onCoins = { if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) com.example.ui.shop.Shop.open.value = "coins" else isAuthOpen = true },
                        onDiamonds = { if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) com.example.ui.shop.Shop.open.value = "diamonds" else isAuthOpen = true },
                        onBell = { isHomeNotifsOpen = true },
                        onAdmin = { isAdminDashboardOpen = true }
                    )
                    // The chat page kept its global search (friends / #hashtags) under the header
                    if (uiState.activeTab == "chat") com.example.ui.feed.HomeSearchPill { globalSearch = true }
                }
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
            ) {
                // Content of one tab (used directly, or as a page of the swipe pager below)
                val tabContent: @Composable (String) -> Unit = { tab ->
                com.example.ui.components.UiPage(tab) { com.example.ui.components.UiSection("page", "ทั้งหน้า") { com.example.ui.components.ComingSoonGate("tab_" + tab, Modifier.fillMaxSize()) { com.example.ui.components.FeatureLockGate(when (tab) { "movie" -> "video"; else -> tab }, Modifier.fillMaxSize()) { when (tab) {
                    "home" -> FeedTab(
                        onOpenSearch = { globalSearch = true },
                        onOpenFindFriends = { viewModel.setActiveTab("discover") },
                        onOpenLive = { viewModel.setActiveTab("live") },
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
                        showCompanion = uiState.uiConfig.bottomNav.showCompanionTab && uiState.uiConfig.isCompanionFeatureEnabled && uiState.currentUser.age >= 18,
                        onOpenCompanion = { viewModel.setActiveTab("companion") },
                        onStartChat = { user ->
                            val conv = uiState.conversations.find { it.participantIds.contains(user.id) }
                            if (conv != null) {
                                viewModel.setActiveChatRoomId(conv.id)
                            } else {
                                viewModel.setActiveTab("chat")
                            }
                        }
                    )

                    "live" -> com.example.ui.social.RealLiveScreen() // no live backend yet: no mock streamers

                    "movie" -> com.example.ui.social.RealVerticalFeed()

                    // Real 1:1 chats from Firebase RTDB (the old mock ChatTab is kept in code but no longer shown)
                    "chat" -> com.example.ui.social.RealChatListScreen()

                    "companion" -> com.example.ui.social.RealCompanionScreen() // real partners (companions/), legacy mock CompanionMainScreen no longer shown

                    "ads" -> com.example.ads.AdsPage()
                    "profile" -> ProfileTab(
                        currentUser = uiState.currentUser,
                        onEditProfile = { isEditProfileOpen = true },
                        onOpenCreatorStudio = { isCreatorStudioOpen = true },
                        onOpenCoins = { com.example.ui.shop.Shop.open.value = "coins" }, // real shop / top-up (old CoinWalletDialog only said "not open")
                        onOpenAccount = { isAuthOpen = true },
                        onOpenPermissions = { isPermissionsOpen = true },
                        onOpenCompanionApply = { isCompanionApplyOpen = true },
                        onLogout = onSignOut
                    )
                } } } } }
                }
                // Swipe left / right between the bottom-bar tabs (same order and admin toggles as BottomNavBar).
                val navTabs = remember(uiState.uiConfig.bottomNav, uiState.uiConfig.isMovieFeatureEnabled) {
                    buildList {
                        if (uiState.uiConfig.bottomNav.showHomeTab) add("home")
                        if (uiState.uiConfig.bottomNav.showDiscoverTab) add("discover")
                        if (com.example.ui.components.TabSwipe.SHOW_SHORT_VIDEO_TAB && uiState.uiConfig.bottomNav.showMovieTab && uiState.uiConfig.isMovieFeatureEnabled) add("movie")
                        if (uiState.uiConfig.bottomNav.showChatTab) add("chat")
                        add("profile")
                    }
                }
                if (uiState.activeTab in navTabs) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = navTabs.indexOf(uiState.activeTab).coerceAtLeast(0)) { navTabs.size }
                    val currentTab by rememberUpdatedState(uiState.activeTab)
                    // bottom bar / code changed the tab → scroll the pager there
                    LaunchedEffect(uiState.activeTab, navTabs) {
                        val i = navTabs.indexOf(uiState.activeTab)
                        if (i >= 0 && i != pagerState.currentPage) pagerState.animateScrollToPage(i)
                    }
                    // user swiped → select that tab
                    LaunchedEffect(pagerState, navTabs) {
                        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { p ->
                            navTabs.getOrNull(p)?.let { if (it != currentTab) viewModel.setActiveTab(it) }
                        }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !com.example.ui.components.TabSwipe.locked,
                        key = { navTabs[it] },
                        modifier = Modifier.fillMaxSize()
                    ) { page -> Box(Modifier.fillMaxSize()) { tabContent(navTabs[page]) } }
                } else tabContent(uiState.activeTab) // live / companion / ads pages: no swiping
                if (uiState.activeTab == "discover") com.example.ads.PlacementBanner("discover", Modifier.align(androidx.compose.ui.Alignment.BottomCenter))

                com.example.ui.components.UiPage("shop") { com.example.ui.shop.ShopHost() }
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
        com.example.ui.admin.AdminConsole(
            onDismiss = { isAdminDashboardOpen = false },
            onOpenUiBuilder = { isUiBuilderOpen = true },
            onOpenApkDownload = { isApkDownloadOpen = true }
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

    val openChatId by com.example.social.SocialRepo.openChat.collectAsStateWithLifecycle()
    openChatId?.let { id -> com.example.ui.components.UiPage("chatroom") { com.example.ui.social.RealChatRoomDialog(chatId = id, onClose = { com.example.social.SocialRepo.openChat.value = null }) } }

    com.example.ads.AdRoomHost()
    val askInterests by com.example.ui.social.InterestsPrompt.show.collectAsStateWithLifecycle()
    if (askInterests) com.example.ui.social.InterestsEditorDialog(onDismiss = { com.example.ui.social.InterestsPrompt.show.value = false }, skippable = true)

    if (isEditProfileOpen) {
        EditProfileDialog(
            currentUser = uiState.currentUser,
            onDismiss = { isEditProfileOpen = false },
            onSave = { dName, bio, age, gen, prov, interests ->
                viewModel.updateProfile(dName, bio, age, gen, prov, interests)
                profileScope.launch {
                    val ok = com.example.social.SocialRepo.saveProfile(dName, bio, age, gen.name.lowercase(), prov, interests)
                    android.widget.Toast.makeText(profileContext, if (ok) "บันทึกโปรไฟล์แล้ว" else "บันทึกโปรไฟล์ไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (isCreatorStudioOpen) {
        com.example.ui.social.CreatorEntryDialog { isCreatorStudioOpen = false }
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

    // Real companion sign-up (friendtalk-brain /companion-apply); the old local-only CompanionApplyDialog is no longer opened.
    if (isCompanionApplyOpen) com.example.ui.social.CompanionSignupDialog { isCompanionApplyOpen = false }

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
