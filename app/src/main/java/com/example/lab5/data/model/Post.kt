package com.example.lab5

import com.google.firebase.Timestamp

data class Post(
    val postId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "", // ex2: show avatar beside posts
    val content: String = "",
    val createdAt: Timestamp? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val commentsCount: Int = 0,

    val imageUrls: List<String> = emptyList(), // ex1: images uploaded with post
    val locationUrl: String = "", // ex7: Google Maps link

    val repostFromPostId: String = "", // ex6: original post id
    val repostFromUsername: String = "", // ex6: original author username
    val repostFromContent: String = "", // ex6: original post content
    val repostFromImageUrls: List<String> = emptyList(), // ex6: original post images
    val repostFromLocationUrl: String = "" // ex6 + ex7: original location link if reposted
)
