package com.example.ui.feed

import com.example.ui.components.uiItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.ui.social.realFeedItems
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import kotlinx.coroutines.launch
import com.example.ui.layout.hasSectionStyle
import com.example.ui.components.ReportDialog
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedTab(
    posts: List<Post>,
    stories: List<Story>,
    users: List<User>,
    currentUser: User,
    clubs: List<Club>,
    uiConfig: AppUiConfig,
    selectedCategory: String,
    searchQuery: String,
    onSelectCategory: (String) -> Unit,
    onLikePost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onAddComment: (postId: String, text: String) -> Unit,
    onCreatePost: (content: String, type: PostType, images: List<String>, videoUrl: String?, tags: List<String>) -> Unit,
    onAddStory: (imageUrl: String) -> Unit,
    onEnterLiveRoom: (String) -> Unit,
    onSubmitReport: (Report) -> Unit,
    onOpenFindFriends: () -> Unit = {},
    onOpenLive: () -> Unit = {}
) {
    var isCreatePostOpen by remember { mutableStateOf(false) }
    var activeCommentPost by remember { mutableStateOf<Post?>(null) }
    var reportTargetPost by remember { mutableStateOf<Post?>(null) }

    val feedConfig = uiConfig.feed
    androidx.compose.runtime.SideEffect { com.example.ui.social.FeedStyle.cfg = feedConfig }
    val availableTabs = feedConfig.availableTabs.ifEmpty {
        listOf("ทั้งหมด", "คนใกล้เคียง", "ออนไลน์", "หาเพื่อน")
    }

    // Filter Posts by Category & Search
    val filteredPosts = remember(posts, selectedCategory, searchQuery) {
        posts.filter { post ->
            val matchSearch = if (searchQuery.isBlank()) true else {
                post.content.contains(searchQuery, ignoreCase = true) ||
                        post.authorName.contains(searchQuery, ignoreCase = true) ||
                        post.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }

            val matchCategory = when (selectedCategory) {
                "ทั้งหมด" -> true
                "คนใกล้เคียง" -> post.location != null && post.location.contains("กรุงเทพ") || post.authorId != currentUser.id
                "ออนไลน์" -> post.type == PostType.LIVE || post.authorIsVerified
                "หาเพื่อน" -> post.tags.any { it.contains("หาเพื่อน") || it.contains("คุย") } || post.type == PostType.TEXT
                else -> true
            }

            matchSearch && matchCategory
        }
    }

    val homeLayout by com.example.service.UiLayoutRepository.home.collectAsState()
    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val realFeed = com.example.ui.social.rememberRealFeedState()
    com.example.ui.social.RealCommentsDialog(realFeed)
    realFeed.openTag?.let { com.example.ui.social.TagFeedDialog(it) { realFeed.openTag = null } }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            refreshScope.launch { com.example.service.UiLayoutRepository.refreshHome(); realFeed.refresh(); refreshing = false }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            val secTabs: LazyListScope.() -> Unit = {
            if (feedConfig.showCategoryTabs) item { HomeChips(realFeed.filter) { realFeed.filter = it } }
            }

            val secBanners: LazyListScope.() -> Unit = {
            // Remote Banners & SDUI Announcements
            val visibleBanners = uiConfig.banners.filter { it.isVisible }
            if (visibleBanners.isNotEmpty()) {
                uiItem("fd1", "Remote Banners & SDUI Announce #1") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleBanners.forEach { banner ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = parseHexColor(banner.backgroundColorHex, Pink600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = banner.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = White
                                        )
                                        if (banner.subtitle.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = banner.subtitle,
                                                fontSize = 12.sp,
                                                color = White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            val secStories: LazyListScope.() -> Unit = {
            if (feedConfig.showStoryRow) {
                item { com.example.ui.components.ComingSoonGate("home_live", Modifier.fillMaxWidth()) { LiveNowSection(onSeeAll = onOpenLive) } }
                item { com.example.ui.components.ComingSoonGate("home_nearby", Modifier.fillMaxWidth()) { NearbyStoriesSection(onSeeAll = onOpenFindFriends) } }
            }
            }

            val secClubs: LazyListScope.() -> Unit = {
            // 3. Optional Clubs Snippet (if enabled in UI config)
            if (false && feedConfig.showClubsSnippet && clubs.isNotEmpty() && selectedCategory == "ทั้งหมด" && searchQuery.isBlank()) {
                uiItem("fd2", "3. Optional Clubs Snippet (if  #2") {
                    ClubsSnippetRow(clubs = clubs, onClubClick = {})
                }
            }
            }

            val secSdc: LazyListScope.() -> Unit = {
            // 3.5 Server-Driven UI Dynamic Components (Real Native Rendering)
            val activeSdc = uiConfig.serverDrivenComponents.filter { it.isVisible }.sortedBy { it.order }
            if (activeSdc.isNotEmpty() && selectedCategory == "ทั้งหมด" && searchQuery.isBlank()) {
                items(activeSdc, key = { it.id }) { sdc ->
                    val sdcBg = parseHexColor(sdc.backgroundColorHex, Slate800)
                    val sdcText = parseHexColor(sdc.textColorHex, White)
                    Surface(
                        shape = RoundedCornerShape(sdc.cornerRadiusDp.dp),
                        color = sdcBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = sdc.marginDp.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(sdc.paddingDp.dp)) {
                            if (sdc.title.isNotBlank()) {
                                Text(
                                    text = sdc.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = sdcText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            if (sdc.text.isNotBlank()) {
                                Text(
                                    text = sdc.text,
                                    fontSize = 12.sp,
                                    color = sdcText.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
            }

            // Real posts from friendtalk-brain (mock posts are no longer shown as real)
            val secFeed: LazyListScope.() -> Unit = { realFeedItems(realFeed) }
            @Suppress("UNUSED_VARIABLE") val secFeedLegacyMock: LazyListScope.() -> Unit = {
            // 4. Post Feed List
            if (filteredPosts.isEmpty()) {
                uiItem("fd3", "4. Post Feed List #3") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "📭", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "ไม่พบโพสต์ที่ตรงกับคำค้นหา" else "ยังไม่มีโพสต์ในหมวดนี้",
                                fontSize = 14.sp,
                                color = Slate400,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        currentUserId = currentUser.id,
                        feedConfig = feedConfig,
                        onLikeClick = { onLikePost(post.id) },
                        onCommentClick = { activeCommentPost = post },
                        onShareClick = { onSharePost(post.id) },
                        onBookmarkClick = { onBookmarkPost(post.id) },
                        onDeleteClick = { onDeletePost(post.id) },
                        onReportClick = { reportTargetPost = post },
                        onUserClick = { /* View User Profile */ },
                        onLiveClick = { liveRoomId -> onEnterLiveRoom(liveRoomId) }
                    )
                }
            }
            }

            // Server-driven Home layout (UI Builder). Falls back to the built-in order when none/invalid.
            val layout = homeLayout
            if (layout == null) {
                secTabs(); secBanners(); secStories(); secClubs(); secSdc(); secFeed()
            } else {
                var feedShown = false
                layout.blocks.filter { it.visible }.forEach { b ->
                    when (b.type) {
                        "section" -> {
                            val scope: LazyListScope = if (b.hasSectionStyle()) com.example.ui.layout.StyledLazyScope(this, b) else this
                            when (b.section) {
                                "tabs" -> scope.secTabs()
                                "banners" -> scope.secBanners()
                                "stories" -> scope.secStories()
                                "clubs" -> scope.secClubs()
                                "sdc" -> scope.secSdc()
                                "feed" -> { if (!feedShown) { feedShown = true; scope.secFeed() } }
                            }
                        }
                        else -> item(key = "ui_" + b.id) { com.example.ui.layout.LayoutBlockView(b) }
                    }
                }
                if (!feedShown) secFeed() // the post feed is always kept
            }
        }

        // Old mock-post FAB hidden: real posts use the composer at the top of the feed
        com.example.ads.PlacementBanner("home", Modifier.align(Alignment.BottomCenter))
    }

    // Dialogs
    if (isCreatePostOpen) {
        CreatePostDialog(
            currentUser = currentUser,
            onDismiss = { isCreatePostOpen = false },
            onCreatePost = { content, type, images, videoUrl, tags ->
                onCreatePost(content, type, images, videoUrl, tags)
            }
        )
    }

    activeCommentPost?.let { post ->
        PostCommentsDialog(
            post = post,
            currentUser = currentUser,
            onDismiss = { activeCommentPost = null },
            onAddComment = { text ->
                onAddComment(post.id, text)
            }
        )
    }

    reportTargetPost?.let { post ->
        ReportDialog(
            targetType = "POST",
            targetId = post.id,
            targetName = "โพสต์ของ ${post.authorName}",
            onDismiss = { reportTargetPost = null },
            onSubmitReport = { report ->
                onSubmitReport(report)
            }
        )
    }
}

@Composable
fun CategoryTabsRow(
    tabs: List<String>,
    selectedTab: String,
    onSelectTab: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs) { tab ->
                val isSelected = selectedTab == tab
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isSelected) Pink500 else Slate800,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onSelectTab(tab) }
                ) {
                    Text(
                        text = tab,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) White else Slate300,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
