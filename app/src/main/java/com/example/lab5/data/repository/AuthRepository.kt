package com.example.lab5
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun register(email: String, password: String, username: String): Result<User> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user!!.uid

            val user = User(
                uid = uid,
                username = username,
                email = email,
                avatarUrl = "" // ex2: empty first, user can update later
            )

            db.collection("users").document(uid).set(user).await()
            user
        }

    suspend fun login(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await().user!!
        }

    fun logout() {
        auth.signOut()
    }
}
