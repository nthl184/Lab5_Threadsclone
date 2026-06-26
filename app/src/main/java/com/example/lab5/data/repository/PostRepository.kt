package com.example.lab5

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PostRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val storage = FirebaseStorage.getInstance() // ex1: Firebase Storage for post images

    fun observeFeed(): Flow<List<Post>> = callbackFlow {
        val listener = db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.toObjects(Post::class.java))
            }
        awaitClose { listener.remove() }
    }

    fun observePostsByUser(uid: String): Flow<List<Post>> = callbackFlow {
        // ex3: profile page shows all posts from selected user
        val listener = db.collection("posts")
            .whereEqualTo("authorId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.toObjects(Post::class.java))
            }
        awaitClose { listener.remove() }
    }

    private suspend fun uploadImages(uid: String, imageUris: List<Uri>): List<String> {
        // ex1: upload selected images and return download URLs
        return imageUris.mapIndexed { index, uri ->
            val ref = storage.reference.child(
                "post_images/$uid/${System.currentTimeMillis()}_$index.jpg"
            )
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        }
    }

    suspend fun createPost(
        uid: String,
        username: String,
        avatarUrl: String,
        content: String,
        imageUris: List<Uri>,
        locationUrl: String
    ): Result<Post> = runCatching {
        // ex1: imageUris
        // ex7: locationUrl
        val imageUrls = uploadImages(uid, imageUris)

        val ref = db.collection("posts").document()
        val post = Post(
            postId = ref.id,
            authorId = uid,
            authorUsername = username,
            authorAvatarUrl = avatarUrl,
            content = content,
            imageUrls = imageUrls,
            locationUrl = locationUrl,
            createdAt = Timestamp.now()
        )
        ref.set(post).await()
        post
    }

    suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        // ex4: delete selected post after UI confirmation dialog
        db.collection("posts").document(postId).delete().await()
    }

    suspend fun repost(
        uid: String,
        username: String,
        avatarUrl: String,
        content: String,
        originalPost: Post
    ): Result<Unit> = runCatching {
        // ex6: repost is stored as a normal post with original post snapshot
        val ref = db.collection("posts").document()
        val post = Post(
            postId = ref.id,
            authorId = uid,
            authorUsername = username,
            authorAvatarUrl = avatarUrl,
            content = content,
            createdAt = Timestamp.now(),
            repostFromPostId = originalPost.postId,
            repostFromUsername = originalPost.authorUsername,
            repostFromContent = originalPost.content,
            repostFromImageUrls = originalPost.imageUrls,
            repostFromLocationUrl = originalPost.locationUrl
        )
        ref.set(post).await()
    }

    suspend fun getUserReactions(uid: String, postIds: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (postIds.isEmpty()) return map

        postIds.chunked(30).forEach { chunk ->
            runCatching {
                db.collection("reactions")
                    .whereEqualTo("userId", uid)
                    .whereIn("postId", chunk)
                    .get().await()
                    .documents
                    .forEach { doc ->
                        val postId = doc.getString("postId") ?: ""
                        val emoji = doc.getString("emoji") ?: ""
                        if (postId.isNotBlank() && emoji.isNotBlank()) map[postId] = emoji
                    }
            }
        }
        return map
    }

    suspend fun toggleReaction(uid: String, postId: String, emoji: String, prevEmoji: String?) =
        runCatching {
            val reactionId = "${uid}_$postId"
            val reactionRef = db.collection("reactions").document(reactionId)
            val postRef = db.collection("posts").document(postId)

            db.runTransaction { tx ->
                val reactions = (tx.get(postRef).get("reactions") as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }
                    ?.mapValues { (it.value as? Long)?.toInt() ?: 0 }
                    ?.toMutableMap() ?: mutableMapOf()

                if (prevEmoji == emoji) {
                    tx.delete(reactionRef)
                    reactions[emoji] = maxOf(0, (reactions[emoji] ?: 0) - 1)
                    if (reactions[emoji] == 0) reactions.remove(emoji)
                } else {
                    prevEmoji?.let {
                        reactions[it] = maxOf(0, (reactions[it] ?: 0) - 1)
                        if (reactions[it] == 0) reactions.remove(it)
                    }
                    tx.set(reactionRef, mapOf("userId" to uid, "postId" to postId, "emoji" to emoji))
                    reactions[emoji] = (reactions[emoji] ?: 0) + 1
                }
                tx.update(postRef, "reactions", reactions)
            }.await()
        }

    fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val listener = db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.toObjects(Comment::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun addComment(
        uid: String,
        username: String,
        postId: String,
        content: String,
        postOwnerId: String
    ): Result<Unit> = runCatching {
        val ref = db.collection("posts").document(postId)
            .collection("comments").document()

        ref.set(
            Comment(
                commentId = ref.id,
                postId = postId,
                authorId = uid,
                authorUsername = username,
                content = content,
                createdAt = Timestamp.now()
            )
        ).await()

        db.collection("posts").document(postId)
            .update("commentsCount", FieldValue.increment(1)).await()

        if (postOwnerId.isNotBlank() && postOwnerId != uid) {
            val notifRef = db.collection("notifications")
                .document(postOwnerId)
                .collection("items")
                .document()
            notifRef.set(
                AppNotification(
                    id = notifRef.id,
                    fromUsername = username,
                    postId = postId,
                    message = if (content.length > 80) content.take(80) + "..." else content,
                    read = false,
                    createdAt = Timestamp.now()
                )
            ).await()
        }
    }

    fun observeNotifications(uid: String): Flow<AppNotification> = callbackFlow {
        val listener = db.collection("notifications")
            .document(uid)
            .collection("items")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                snap?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val notif = AppNotification(
                            id = doc.id,
                            fromUsername = doc.getString("fromUsername") ?: "",
                            postId = doc.getString("postId") ?: "",
                            message = doc.getString("message") ?: "",
                            read = doc.getBoolean("read") ?: false,
                            createdAt = doc.getTimestamp("createdAt")
                        )
                        trySend(notif)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun markNotificationRead(uid: String, notifId: String) {
        if (notifId.isBlank()) return
        db.collection("notifications")
            .document(uid)
            .collection("items")
            .document(notifId)
            .update("read", true)
    }
}
