package com.example.lab5

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val storage = FirebaseStorage.getInstance() // ex2: upload profile avatar

    suspend fun getUser(uid: String): Result<User> = runCatching {
        db.collection("users")
            .document(uid)
            .get()
            .await()
            .toObject(User::class.java) ?: User(uid = uid)
    }

    suspend fun updateProfile(uid: String, username: String, avatarUri: Uri?): Result<Unit> =
        runCatching {
            val data = mutableMapOf<String, Any>(
                "username" to username
            )

            // ex2: update avatar only when user selects a new image
            if (avatarUri != null) {
                val ref = storage.reference.child("avatars/$uid/avatar_${System.currentTimeMillis()}.jpg")
                ref.putFile(avatarUri).await()
                data["avatarUrl"] = ref.downloadUrl.await().toString()
            }

            db.collection("users").document(uid).update(data).await()
        }

    suspend fun searchUsers(keyword: String): Result<List<User>> = runCatching {
        // ex8: simple search for lab. Firestore has no native contains query.
        db.collection("users")
            .orderBy("username", Query.Direction.ASCENDING)
            .get()
            .await()
            .toObjects(User::class.java)
            .filter { it.username.contains(keyword, ignoreCase = true) }
    }

    suspend fun followUser(
        currentUid: String,
        targetUid: String,
        currentUsername: String
    ): Result<Unit> = runCatching {
        // ex5: one document per follow relationship
        val followId = "${currentUid}_$targetUid"
        db.collection("follows").document(followId).set(
            mapOf(
                "followerId" to currentUid,
                "followingId" to targetUid,
                "fromUsername" to currentUsername,
                "createdAt" to Timestamp.now()
            )
        ).await()

        // ex5: create notification for followed user
        val notifRef = db.collection("notifications")
            .document(targetUid)
            .collection("items")
            .document()

        notifRef.set(
            AppNotification(
                id = notifRef.id,
                fromUsername = currentUsername,
                postId = "",
                message = "$currentUsername followed you",
                read = false,
                createdAt = Timestamp.now()
            )
        ).await()
    }

    suspend fun isFollowing(currentUid: String, targetUid: String): Boolean {
        // ex5: check follow state for profile button text
        return db.collection("follows")
            .document("${currentUid}_$targetUid")
            .get()
            .await()
            .exists()
    }

    suspend fun blockUser(currentUid: String, targetUid: String): Result<Unit> = runCatching {
        // ex9: if A blocks B, B cannot view A profile/posts
        val blockId = "${currentUid}_$targetUid"
        db.collection("blocks").document(blockId).set(
            mapOf(
                "blockerId" to currentUid,
                "blockedId" to targetUid,
                "createdAt" to Timestamp.now()
            )
        ).await()
    }

    suspend fun isBlockedByTarget(currentUid: String, targetUid: String): Boolean {
        // ex9: true means target user blocked current user
        return db.collection("blocks")
            .document("${targetUid}_$currentUid")
            .get()
            .await()
            .exists()
    }

    suspend fun getUserIdsWhoBlockedMe(currentUid: String): Set<String> {
        // ex9: used to hide posts from users who blocked me
        return db.collection("blocks")
            .whereEqualTo("blockedId", currentUid)
            .get()
            .await()
            .documents
            .mapNotNull { it.getString("blockerId") }
            .toSet()
    }
}
