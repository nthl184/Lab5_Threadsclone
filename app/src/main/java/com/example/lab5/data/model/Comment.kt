package com.example.lab5

import com.google.firebase.Timestamp

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val content: String = "",
    val createdAt: Timestamp? = null
)
