package com.example.data

import com.example.model.*

object MockData {
    val initialUsers = listOf(
        User(
            id = "user_linlin",
            username = "linlin_cute",
            displayName = "หลินหลิน 🌸",
            avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            coverPhoto = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
            age = 22,
            gender = Gender.FEMALE,
            bio = "สตรีมเมอร์ & สายกิน 🍕 ไลฟ์ทุกวัน 2 ทุ่ม มาคุยกันน้าาา 💕",
            role = UserRole.CREATOR,
            isCreator = true,
            isVerified = true,
            isOnline = true,
            lastActive = "กำลังไลฟ์สด 🔴",
            coins = 4500,
            diamonds = 12500,
            followersCount = 8900,
            followingCount = 120,
            friendsCount = 85,
            likesCount = 45000,
            location = UserLocation("กรุงเทพมหานคร (สยาม)", 1.2, true),
            badges = listOf("🔥 Top Creator", "🎤 สตรีมเมอร์"),
            interests = listOf("ดนตรี", "เกม", "กินเที่ยว", "ร้องเพลง"),
            giftsReceivedTotal = 1250
        ),
        User(
            id = "user_fah",
            username = "fah_sky",
            displayName = "ฟ้าใส (Fahsai)",
            avatar = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80",
            coverPhoto = "https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=800&auto=format&fit=crop&q=80",
            age = 23,
            gender = Gender.FEMALE,
            bio = "ฟรีแลนซ์ ดีไซเนอร์ ชอบคุยเรื่องงานอาร์ตและแมว 🐱🎨",
            role = UserRole.USER,
            isCreator = false,
            isVerified = true,
            isOnline = true,
            lastActive = "ออนไลน์ 5 นาทีที่แล้ว",
            coins = 800,
            diamonds = 150,
            followersCount = 1200,
            followingCount = 310,
            friendsCount = 110,
            likesCount = 5600,
            location = UserLocation("นนทบุรี", 3.8, true),
            badges = listOf("🎨 สายอาร์ต", "🐱 ทาสแมว"),
            interests = listOf("ออกแบบ", "แมว", "กาแฟ", "นิทรรศการ"),
            giftsReceivedTotal = 95
        ),
        User(
            id = "user_arty",
            username = "arty_rock",
            displayName = "อาร์ตี้ กีตาร์",
            avatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80",
            coverPhoto = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
            age = 26,
            gender = Gender.MALE,
            bio = "นักดนตรีกลางคืน ชอบเล่นกีตาร์อะคูสติก 🎸🎶",
            role = UserRole.CREATOR,
            isCreator = true,
            isVerified = false,
            isOnline = false,
            lastActive = "ออฟไลน์ 1 ชม. ที่แล้ว",
            coins = 3200,
            diamonds = 4800,
            followersCount = 4300,
            followingCount = 200,
            friendsCount = 95,
            likesCount = 18900,
            location = UserLocation("เชียงใหม่", 15.0, false),
            badges = listOf("🎸 นักดนตรี"),
            interests = listOf("ดนตรี", "กีตาร์", "แต่งเพลง"),
            giftsReceivedTotal = 340
        )
    )

    val initialStories = listOf(
        Story(
            id = "story_linlin",
            userId = "user_linlin",
            userName = "หลินหลิน 🌸",
            userAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
            timestamp = "15 นาทีที่แล้ว",
            isViewed = false,
            isLiveNow = true,
            liveViewerCount = 1420
        ),
        Story(
            id = "story_fah",
            userId = "user_fah",
            userName = "ฟ้าใส",
            userAvatar = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80",
            imageUrl = "https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=800&auto=format&fit=crop&q=80",
            timestamp = "1 ชม. ที่แล้ว",
            isViewed = false
        ),
        Story(
            id = "story_arty",
            userId = "user_arty",
            userName = "อาร์ตี้",
            userAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80",
            imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
            timestamp = "3 ชม. ที่แล้ว",
            isViewed = true
        )
    )

    val initialPosts = listOf(
        Post(
            id = "post_1",
            authorId = "user_linlin",
            authorName = "หลินหลิน 🌸",
            authorAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            authorUsername = "linlin_cute",
            authorIsVerified = true,
            authorBadges = listOf("🔥 Top Creator", "🎤 สตรีมเมอร์"),
            location = "สยามพารากอน, กรุงเทพฯ",
            timestamp = "10 นาทีที่แล้ว",
            type = PostType.LIVE,
            content = "🔴 ตอนนี้กำลังเปิดห้องไลฟ์พูดคุย ร้องเพลงอะคูสติกชิลล์ๆ เข้ามาร่วมพูดคุยและส่งกำลังใจกันได้น้าาา มีกิจกรรมแจกของรางวัลด้วยจ้า! ✨🎵",
            images = listOf("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80"),
            liveRoomId = "live_linlin_01",
            liveViewerCount = 1420,
            likesCount = 384,
            commentsCount = 42,
            sharesCount = 18,
            isLiked = true,
            comments = listOf(
                PostComment("c1", "post_1", "user_bank", "แบงค์ (Bank)", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80", "กำลังเข้าไปดูเลยครับบ ร้องเพราะมาก!", "5 นาทีที่แล้ว", 3, true),
                PostComment("c2", "post_1", "user_fah", "ฟ้าใส", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80", "สวยมากก เพลงเพราะฟินสุดๆ 💕", "2 นาทีที่แล้ว", 1, false)
            ),
            tags = listOf("ไลฟ์สด", "ร้องเพลง", "หาเพื่อนคุย")
        ),
        Post(
            id = "post_2",
            authorId = "user_fah",
            authorName = "ฟ้าใส (Fahsai)",
            authorAvatar = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80",
            authorUsername = "fah_sky",
            authorIsVerified = true,
            authorBadges = listOf("🎨 สายอาร์ต"),
            location = "คาเฟ่ อารีย์, กรุงเทพฯ",
            timestamp = "45 นาทีที่แล้ว",
            type = PostType.MULTI_IMAGE,
            content = "วันหยุดกับการตามล่ากาแฟ Dirty แสนอร่อย ☕ บรรยากาศสงบเหมาะกับการนั่งวาดรูปมาก ใครชอบคาเฟ่สไตล์นี้แนะนำเลยจ้า แปะพิกัดไว้ให้แล้วน้า 🌿",
            images = listOf(
                "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800&auto=format&fit=crop&q=80"
            ),
            likesCount = 512,
            commentsCount = 28,
            sharesCount = 14,
            isLiked = false,
            tags = listOf("คาเฟ่", "กาแฟ", "อารีย์", "วันหยุด")
        ),
        Post(
            id = "post_3",
            authorId = "user_arty",
            authorName = "อาร์ตี้ กีตาร์",
            authorAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80",
            authorUsername = "arty_rock",
            authorIsVerified = false,
            authorBadges = listOf("🎸 นักดนตรี"),
            location = "เชียงใหม่",
            timestamp = "2 ชม. ที่แล้ว",
            type = PostType.VIDEO,
            content = "ลองแกะเพลงใหม่ดูครับ ท่อนโซโล่นี้ฝึกอยู่นานมาก ใครชอบฟังเพลงแนวนี้คอมเมนต์ทักทายกันได้ครับ 🎸🔥",
            images = listOf("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80"),
            videoUrl = "https://example.com/guitar_solo.mp4",
            videoDuration = "01:24",
            likesCount = 289,
            commentsCount = 19,
            sharesCount = 7,
            isLiked = false,
            tags = listOf("กีตาร์", "ดนตรี", "Cover")
        ),
        Post(
            id = "post_4",
            authorId = "user_bank",
            authorName = "แบงค์ (Bank)",
            authorAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80",
            authorUsername = "bank_dev",
            authorIsVerified = true,
            authorBadges = listOf("ผู้ใช้ดาวรุ่ง ⭐"),
            location = "สยามสแควร์",
            timestamp = "4 ชม. ที่แล้ว",
            type = PostType.TEXT,
            content = "มีใครอยู่แถวสยามบ้างครับ เย็นนี้มีงานดนตรีเปิดหมวก มาเดินเล่นฟังเพลงหาเพื่อนคุยกันได้นะ! 🎶👋",
            images = emptyList(),
            likesCount = 142,
            commentsCount = 12,
            sharesCount = 3,
            isLiked = false,
            tags = listOf("สยาม", "หาเพื่อน", "ดนตรีเปิดหมวก")
        ),
        Post(
            id = "post_5",
            authorId = "user_fah",
            authorName = "ฟ้าใส (Fahsai)",
            authorAvatar = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80",
            authorUsername = "fah_sky",
            authorIsVerified = true,
            authorBadges = listOf("🎨 สายอาร์ต"),
            location = "BACC หอศิลป์กรุงเทพฯ",
            timestamp = "6 ชม. ที่แล้ว",
            type = PostType.SHARED_POST,
            content = "งานนิทรรศการนี้น่าไปมากก ใครสายอาร์ตห้ามพลาดเลยนะคะ แชร์ต่อให้ทุกคนดูกันค่ะ 🖼️✨",
            sharedPost = SharedPostContent(
                originalPostId = "post_orig_1",
                originalAuthorName = "Art Community Thailand",
                originalAuthorAvatar = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=400&auto=format&fit=crop&q=80",
                originalContent = "เปิดตัวนิทรรศการศิลปะร่วมสมัย Modern Thai Visual Art เข้าชมฟรีตลอดเดือนนี้!",
                originalImageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=800&auto=format&fit=crop&q=80",
                originalTimestamp = "1 วันที่แล้ว"
            ),
            likesCount = 310,
            commentsCount = 15,
            sharesCount = 22,
            isLiked = true,
            tags = listOf("ศิลปะ", "นิทรรศการ", "หอศิลป์")
        )
    )

    val initialLiveRooms = listOf(
        LiveRoom(
            id = "live_linlin_01",
            hostId = "user_linlin",
            hostName = "หลินหลิน 🌸",
            hostAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            hostUsername = "linlin_cute",
            title = "ร้องเพลงอะคูสติกชิลล์ๆ คุยเรื่องความรัก & PK Battle! 🎵💕",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
            tags = listOf("ร้องเพลง", "PK Match", "สาวน่ารัก"),
            viewerCount = 1420,
            likesCount = 8900,
            diamondsEarned = 3450,
            isLive = true,
            isPkMode = true,
            pkState = PkState(
                isPkActive = true,
                opponentId = "user_arty",
                opponentName = "อาร์ตี้ กีตาร์",
                opponentAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80",
                myScore = 1250,
                opponentScore = 980,
                timeLeftSeconds = 120
            ),
            recentComments = listOf(
                LiveComment("lc1", "user_bank", "แบงค์ (Bank)", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80", "เสียงเพราะมากก สู้ๆ ครับ!"),
                LiveComment("lc2", "user_fah", "ฟ้าใส", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80", "ส่งหัวใจให้รัวๆ 💕", isGift = true, giftIcon = "💖")
            ),
            topGifters = listOf(
                TopGifterRecord(1, "user_bank", "แบงค์ (Bank)", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80", 500),
                TopGifterRecord(2, "user_fah", "ฟ้าใส", "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80", 250)
            )
        )
    )

    val initialConversations = listOf(
        Conversation(
            id = "conv_linlin",
            isGroup = false,
            participantIds = listOf("user_fah", "user_linlin"),
            lastMessage = "ขอบคุณที่แวะมาดูไลฟ์นะค้าบบ 🌸",
            lastMessageTimestamp = "10:15",
            unreadCounts = mapOf("user_fah" to 1)
        ),
        Conversation(
            id = "conv_fah",
            isGroup = false,
            participantIds = listOf("user_bank", "user_fah"),
            lastMessage = "พรุ่งนี้เจอกันที่คาเฟ่นะคะ ☕",
            lastMessageTimestamp = "เมื่อวาน",
            unreadCounts = mapOf("user_bank" to 0)
        )
    )

    val initialMessages = listOf(
        ChatMessage(
            id = "m1",
            senderId = "user_linlin",
            senderName = "หลินหลิน",
            senderAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            conversationId = "conv_linlin",
            text = "สวัสดีค่ะ ดีใจที่ได้คุยกันนะคะ!",
            timestamp = "10:12"
        ),
        ChatMessage(
            id = "m2",
            senderId = "user_linlin",
            senderName = "หลินหลิน",
            senderAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80",
            conversationId = "conv_linlin",
            text = "ขอบคุณที่แวะมาดูไลฟ์นะค้าบบ 🌸",
            timestamp = "10:15"
        )
    )

    val initialClubs = listOf(
        Club(
            id = "club_cafe",
            name = "Cafe Hopper กรุงเทพฯ",
            category = "กินเที่ยว",
            icon = "☕",
            coverUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800&auto=format&fit=crop&q=80",
            description = "คอมมูนิตี้คนรักกาแฟ รีวิวคาเฟ่เปิดใหม่ ชวนกันไป Hopping",
            membersCount = 3450,
            isJoined = true
        ),
        Club(
            id = "club_music",
            name = "Acoustic Live & ร้องเพลง",
            category = "ดนตรี",
            icon = "🎸",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
            description = "ห้องคนรักเสียงเพลง แลกเปลี่ยนคอร์ด ชวนกันแจม",
            membersCount = 2180,
            isJoined = false
        )
    )

    val initialForumTopics = listOf(
        ForumTopic(
            id = "ft_1",
            clubId = "club_cafe",
            authorName = "แบงค์",
            authorAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80",
            title = "แนะนำคาเฟ่เปิดใหม่แถวอารีย์ แสงสวย กาแฟดีมาก!",
            preview = "เมื่อวานไปเจอร้านนี้มา บรรยากาศชิลล์มาก...",
            repliesCount = 8
        )
    )

    val initialReports = listOf(
        Report(
            id = "rep_1",
            reporterId = "user_fah",
            reporterName = "ฟ้าใส",
            targetType = "USER",
            targetId = "user_spammer",
            targetName = "spammer_bot",
            reason = "ส่งข้อความโฆษณาชวนเชื่อรบกวน",
            details = "ส่งลิงก์ภายนอกที่ไม่ปลอดภัยมาในแชตส่วนตัว",
            timestamp = "วันนี้ 09:30"
        )
    )

    val initialWithdrawals = listOf(
        WithdrawalRequest(
            id = "wd_1",
            userId = "user_linlin",
            userName = "หลินหลิน",
            amountDiamonds = 5000,
            amountBaht = 1000.0,
            bankName = "ธนาคารกสิกรไทย",
            accountNumber = "123-4-56789-0",
            accountHolder = "น.ส. หลินหลิน สดใส",
            status = "PENDING",
            requestedAt = "วันนี้ 11:00"
        )
    )

    val initialAdminLogs = listOf(
        AdminLog(
            id = "log_1",
            adminName = "ผู้ดูแลระบบ (System Admin)",
            action = "อนุมัติผู้ใช้ยืนยันตัวตน (KYC)",
            details = "อนุมัติ KYC สำหรับบัญชี user_linlin",
            timestamp = "05/10/2026 14:20"
        )
    )

    val initialNotifications = listOf(
        AppNotification(
            id = "notif_1",
            title = "หลินหลิน กำลังไลฟ์สด 🔴",
            message = "เข้ามาร่วมฟังเพลงและพูดคุยกันเลย!",
            type = "LIVE",
            timestamp = "5 นาทีที่แล้ว",
            avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80"
        ),
        AppNotification(
            id = "notif_2",
            title = "มีคนกดไลก์โพสต์ของคุณ ❤️",
            message = "ฟ้าใส กดไลก์โพสต์ของคุณ 'มีใครอยู่แถวสยามบ้างครับ'",
            type = "LIKE",
            timestamp = "20 นาทีที่แล้ว",
            avatarUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80"
        )
    )

    val giftsList = listOf(
        GiftItem("gift_heart", "หัวใจมินิ", "💖", 10, "POP"),
        GiftItem("gift_flower", "ดอกกุหลาบ", "🌹", 50, "FLOAT"),
        GiftItem("gift_tea", "ชานมไข่มุก", "🧋", 100, "POP"),
        GiftItem("gift_crown", "มงกุฎทอง", "👑", 500, "ROYAL"),
        GiftItem("gift_rocket", "จรวดซูเปอร์สตาร์", "🚀", 1000, "BLAST")
    )
}
