package com.example.lab5

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val authRepo = AuthRepository()
    private val postRepo = PostRepository()
    private val userRepo = UserRepository()

    private val _authState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val authState: StateFlow<UiState<Unit>> = _authState.asStateFlow()

    val isLoggedIn: Boolean get() = authRepo.isLoggedIn
    private val currentUid: String? get() = authRepo.currentUid
    val currentUserId: String? get() = currentUid // ex4, ex5, ex9: UI needs to know current user

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow() // ex2: own profile data

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _profileUser = MutableStateFlow<User?>(null)
    val profileUser: StateFlow<User?> = _profileUser.asStateFlow() // ex2: selected profile data

    private val _profilePosts = MutableStateFlow<List<Post>>(emptyList())
    val profilePosts: StateFlow<List<Post>> = _profilePosts.asStateFlow() // ex3: selected profile posts

    private val _profileBlocked = MutableStateFlow(false)
    val profileBlocked: StateFlow<Boolean> = _profileBlocked.asStateFlow() // ex9

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow() // ex5

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow() // ex8

    private val _postState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val postState: StateFlow<UiState<Unit>> = _postState.asStateFlow()
    fun resetPostState() { _postState.value = UiState.Idle }

    private val _profileState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val profileState: StateFlow<UiState<Unit>> = _profileState.asStateFlow() // ex2, ex5, ex9
    fun resetProfileState() { _profileState.value = UiState.Idle }

    private val _userReactions = MutableStateFlow<Map<String, String>>(emptyMap())
    val userReactions: StateFlow<Map<String, String>> = _userReactions.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _commentState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val commentState: StateFlow<UiState<Unit>> = _commentState.asStateFlow()
    fun resetCommentState() { _commentState.value = UiState.Idle }

    private val _newNotification = MutableStateFlow<AppNotification?>(null)
    val newNotification: StateFlow<AppNotification?> = _newNotification.asStateFlow()

    fun register(email: String, password: String, username: String) {
        _authState.value = UiState.Loading
        viewModelScope.launch {
            authRepo.register(email, password, username)
                .onSuccess {
                    _username.value = it.username
                    _currentUser.value = it
                    _authState.value = UiState.Success()
                }
                .onFailure { _authState.value = UiState.Error(it.message ?: "Đăng ký thất bại") }
        }
    }

    fun login(email: String, password: String) {
        _authState.value = UiState.Loading
        viewModelScope.launch {
            authRepo.login(email, password)
                .onSuccess {
                    loadCurrentUser()
                    _authState.value = UiState.Success()
                }
                .onFailure { _authState.value = UiState.Error(it.message ?: "Đăng nhập thất bại") }
        }
    }

    fun logout() {
        authRepo.logout()
        _posts.value = emptyList()
        _profilePosts.value = emptyList()
        _searchResults.value = emptyList()
        _username.value = ""
        _currentUser.value = null
        _authState.value = UiState.Idle
    }

    fun resetAuthState() { _authState.value = UiState.Idle }

    fun loadCurrentUser() {
        val uid = currentUid ?: return
        viewModelScope.launch {
            userRepo.getUser(uid)
                .onSuccess {
                    _currentUser.value = it
                    _username.value = it.username
                }
        }
    }

    fun listenFeed() {
        val uid = currentUid ?: return
        postRepo.observeFeed()
            .onEach { allPosts ->
                // ex9: hide posts from users who blocked current user
                val blockers = userRepo.getUserIdsWhoBlockedMe(uid)
                _posts.value = allPosts.filter { it.authorId !in blockers }
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun createPost(content: String, imageUris: List<Uri>, locationUrl: String) {
        // ex1: imageUris
        // ex7: locationUrl
        val uid = currentUid ?: return
        val user = _currentUser.value
        _postState.value = UiState.Loading
        viewModelScope.launch {
            postRepo.createPost(
                uid = uid,
                username = user?.username ?: _username.value,
                avatarUrl = user?.avatarUrl ?: "",
                content = content,
                imageUris = imageUris,
                locationUrl = locationUrl
            )
                .onSuccess { _postState.value = UiState.Success() }
                .onFailure { _postState.value = UiState.Error(it.message ?: "Đăng bài thất bại") }
        }
    }

    fun deletePost(postId: String) {
        // ex4
        viewModelScope.launch {
            postRepo.deletePost(postId)
                .onFailure { _postState.value = UiState.Error(it.message ?: "Xóa bài thất bại") }
        }
    }

    fun repost(content: String, originalPost: Post) {
        // ex6
        val uid = currentUid ?: return
        val user = _currentUser.value
        _postState.value = UiState.Loading
        viewModelScope.launch {
            postRepo.repost(
                uid = uid,
                username = user?.username ?: _username.value,
                avatarUrl = user?.avatarUrl ?: "",
                content = content,
                originalPost = originalPost
            )
                .onSuccess { _postState.value = UiState.Success() }
                .onFailure { _postState.value = UiState.Error(it.message ?: "Repost thất bại") }
        }
    }

    fun loadUserProfile(uid: String) {
        // ex2 + ex9
        val me = currentUid ?: return
        viewModelScope.launch {
            _profileBlocked.value = userRepo.isBlockedByTarget(me, uid)

            if (_profileBlocked.value) {
                _profileUser.value = null
                _profilePosts.value = emptyList()
                return@launch
            }

            userRepo.getUser(uid).onSuccess { _profileUser.value = it }
            _isFollowing.value = if (me == uid) false else userRepo.isFollowing(me, uid)
        }
    }

    fun listenPostsByUser(uid: String) {
        // ex3 + ex9
        val me = currentUid ?: return
        postRepo.observePostsByUser(uid)
            .onEach { list ->
                val blocked = userRepo.isBlockedByTarget(me, uid)
                _profileBlocked.value = blocked
                _profilePosts.value = if (blocked) emptyList() else list
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun updateProfile(username: String, avatarUri: Uri?) {
        // ex2
        val uid = currentUid ?: return
        _profileState.value = UiState.Loading
        viewModelScope.launch {
            userRepo.updateProfile(uid, username, avatarUri)
                .onSuccess {
                    loadCurrentUser()
                    loadUserProfile(uid)
                    _profileState.value = UiState.Success()
                }
                .onFailure { _profileState.value = UiState.Error(it.message ?: "Cập nhật profile thất bại") }
        }
    }

    fun searchUsers(keyword: String) {
        // ex8
        viewModelScope.launch {
            if (keyword.isBlank()) {
                _searchResults.value = emptyList()
                return@launch
            }
            userRepo.searchUsers(keyword)
                .onSuccess { users ->
                    _searchResults.value = users.filter { it.uid != currentUid }
                }
        }
    }

    fun followUser(targetUid: String) {
        // ex5
        val me = currentUid ?: return
        if (me == targetUid) return
        _profileState.value = UiState.Loading
        viewModelScope.launch {
            userRepo.followUser(me, targetUid, _username.value)
                .onSuccess {
                    _isFollowing.value = true
                    _profileState.value = UiState.Success()
                }
                .onFailure { _profileState.value = UiState.Error(it.message ?: "Follow thất bại") }
        }
    }

    fun blockUser(targetUid: String) {
        // ex9
        val me = currentUid ?: return
        if (me == targetUid) return
        _profileState.value = UiState.Loading
        viewModelScope.launch {
            userRepo.blockUser(me, targetUid)
                .onSuccess { _profileState.value = UiState.Success() }
                .onFailure { _profileState.value = UiState.Error(it.message ?: "Block thất bại") }
        }
    }

    fun loadUserReactions(postIds: List<String>) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _userReactions.value = postRepo.getUserReactions(uid, postIds)
        }
    }

    fun react(postId: String, emoji: String) {
        val uid = currentUid ?: return
        val prevEmoji = _userReactions.value[postId]
        viewModelScope.launch {
            postRepo.toggleReaction(uid, postId, emoji, prevEmoji)
                .onSuccess {
                    _userReactions.value = _userReactions.value.toMutableMap().apply {
                        if (prevEmoji == emoji) remove(postId) else put(postId, emoji)
                    }
                }
        }
    }

    fun listenComments(postId: String) {
        postRepo.observeComments(postId)
            .onEach { _comments.value = it }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun addComment(postId: String, content: String) {
        val uid = currentUid ?: return
        _commentState.value = UiState.Loading
        viewModelScope.launch {
            val ownerId = posts.value.find { it.postId == postId }?.authorId
                ?: profilePosts.value.find { it.postId == postId }?.authorId
                ?: ""
            postRepo.addComment(uid, _username.value, postId, content, ownerId)
                .onSuccess { _commentState.value = UiState.Success() }
                .onFailure { _commentState.value = UiState.Error(it.message ?: "Bình luận thất bại") }
        }
    }

    fun listenNotifications() {
        val uid = currentUid ?: return
        postRepo.observeNotifications(uid)
            .onEach { _newNotification.value = it }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun markNotificationRead(notifId: String) {
        val uid = currentUid ?: return
        postRepo.markNotificationRead(uid, notifId)
    }

    fun clearNotification() { _newNotification.value = null }
}
