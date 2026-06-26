package com.example.lab5

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Locale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage

private enum class AppPage {
    FEED, PROFILE, SEARCH
}

@Composable
fun ThreadsApp(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val newNotif by viewModel.newNotification.collectAsState() // Khai báo lắng nghe
    val context = LocalContext.current

    // Bắt sự kiện có thông báo mới và gọi hàm showLocalNotification
    LaunchedEffect(newNotif) {
        newNotif?.let { notif ->
            showLocalNotification(
                context = context,
                title = "Threads Clone",
                body = notif.message,
                postId = notif.postId
            )
            viewModel.markNotificationRead(notif.id)
            viewModel.clearNotification() // Xóa cache sau khi đã show
        }
    }

    if (currentUser == null) {
        AuthScreen(vm = viewModel)
    } else {
        HomeScreen(vm = viewModel)
    }
}

@Composable
fun AuthScreen(vm: MainViewModel) {
    val authState by vm.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }

    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(authState) {
        if (authState is UiState.Error) {
            snackbarHost.showSnackbar((authState as UiState.Error).message)
            vm.resetAuthState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Threads Lite",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (!isLogin) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            val isLoading = authState is UiState.Loading

            Button(
                onClick = {
                    if (isLogin) {
                        vm.login(email.trim(), password)
                    } else {
                        vm.register(email.trim(), password, username.trim())
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isLogin) "Đăng nhập" else "Đăng ký")
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    isLogin = !isLogin
                    vm.resetAuthState()
                }
            ) {
                Text(
                    if (isLogin) {
                        "Chưa có tài khoản? Đăng ký"
                    } else {
                        "Đã có tài khoản? Đăng nhập"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val username by vm.username.collectAsState()
    val currentUser by vm.currentUser.collectAsState()

    var page by remember { mutableStateOf(AppPage.FEED) }
    var selectedProfileUid by remember { mutableStateOf(vm.currentUserId ?: "") }

    LaunchedEffect(Unit) {
        vm.listenFeed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Threads Lite", fontWeight = FontWeight.Bold)
                },
                actions = {
                    TextButton(onClick = { page = AppPage.FEED }) {
                        Text("Feed")
                    }

                    TextButton(onClick = { page = AppPage.SEARCH }) {
                        Text("Search") // ex8
                    }

                    TextButton(
                        onClick = {
                            selectedProfileUid = vm.currentUserId ?: ""
                            page = AppPage.PROFILE
                        }
                    ) {
                        Text("@$username") // ex2
                    }

                    TextButton(onClick = { vm.logout() }) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                AppPage.FEED -> FeedScreen(
                    vm = vm,
                    onOpenProfile = { uid ->
                        selectedProfileUid = uid
                        page = AppPage.PROFILE
                    }
                )

                AppPage.PROFILE -> ProfileScreen(
                    vm = vm,
                    profileUid = selectedProfileUid.ifBlank { currentUser?.uid ?: "" },
                    onBack = { page = AppPage.FEED },
                    onOpenProfile = { uid -> selectedProfileUid = uid }
                )

                AppPage.SEARCH -> SearchScreen(
                    vm = vm,
                    onUserClick = { uid ->
                        selectedProfileUid = uid
                        page = AppPage.PROFILE
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedScreen(
    vm: MainViewModel,
    onOpenProfile: (String) -> Unit
) {
    val posts by vm.posts.collectAsState()
    val postState by vm.postState.collectAsState()
    val userReactions by vm.userReactions.collectAsState()
    val currentUser by vm.currentUser.collectAsState()

    var content by remember { mutableStateOf("") }
    var locationUrl by remember { mutableStateOf("") } // ex7
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) } // ex1
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var repostPost by remember { mutableStateOf<Post?>(null) } // ex6

    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(posts) {
        if (posts.isNotEmpty()) {
            vm.loadUserReactions(posts.map { it.postId })
        }
    }

    LaunchedEffect(postState) {
        when (postState) {
            is UiState.Success -> {
                content = ""
                locationUrl = ""
                selectedImages = emptyList()
                vm.resetPostState()
            }

            is UiState.Error -> {
                snackbarHost.showSnackbar((postState as UiState.Error).message)
                vm.resetPostState()
            }

            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ComposeBox(
                content = content,
                selectedImages = selectedImages,
                locationUrl = locationUrl,
                isLoading = postState is UiState.Loading,
                onContentChange = { content = it },
                onImagesChange = { selectedImages = it },
                onLocationChange = { locationUrl = it },
                onSubmit = { vm.createPost(content, selectedImages, locationUrl) }
            )

            HorizontalDivider()

            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Chưa có bài nào", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(posts, key = { it.postId }) { post ->
                        PostItem(
                            post = post,
                            currentUserId = currentUser?.uid ?: "",
                            userReaction = userReactions[post.postId],
                            onReact = { emoji -> vm.react(post.postId, emoji) },
                            onCommentClick = { selectedPost = post },
                            onDeleteClick = { vm.deletePost(post.postId) }, // ex4
                            onRepostClick = { repostPost = post }, // ex6
                            onOpenProfile = { onOpenProfile(post.authorId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selectedPost?.let { post ->
        CommentSheet(
            post = post,
            vm = vm,
            onDismiss = { selectedPost = null }
        )
    }

    repostPost?.let { post ->
        RepostDialog(
            post = post,
            onDismiss = { repostPost = null },
            onRepost = { text ->
                vm.repost(text, post)
                repostPost = null
            }
        )
    }
}

@Composable
private fun ComposeBox(
    content: String,
    selectedImages: List<Uri>,
    locationUrl: String,
    isLoading: Boolean,
    onContentChange: (String) -> Unit,
    onImagesChange: (List<Uri>) -> Unit,
    onLocationChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    // ex1
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        onImagesChange(uris.take(5))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                placeholder = { Text("Bạn đang nghĩ gì?") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    val imageRuleOk = selectedImages.isEmpty() || selectedImages.size >= 2
                    if (content.isNotBlank() && imageRuleOk) {
                        onSubmit()
                    }
                },
                enabled = !isLoading &&
                        content.isNotBlank() &&
                        (selectedImages.isEmpty() || selectedImages.size >= 2)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Đăng")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { imagePicker.launch("image/*") }) {
                Text("Add images") // ex1
            }

            Text("${selectedImages.size} selected", fontSize = 12.sp)

            Spacer(Modifier.width(8.dp))

            if (selectedImages.isNotEmpty() && selectedImages.size < 2) {
                Text(
                    text = "Choose at least 2 images",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (selectedImages.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                selectedImages.take(n = 5).forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .size(72.dp)
                            .padding(end = 6.dp)
                    )
                }
            }
        }

        // ex7
        OutlinedTextField(
            value = locationUrl,
            onValueChange = onLocationChange,
            label = { Text("Google Maps link") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun SearchScreen(
    vm: MainViewModel,
    onUserClick: (String) -> Unit
) {
    // ex8
    val results by vm.searchResults.collectAsState()
    var keyword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Search user", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = keyword,
            onValueChange = {
                keyword = it
                vm.searchUsers(it)
            },
            label = { Text("Enter username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (results.isEmpty() && keyword.isNotBlank()) {
                item {
                    Text("No users found", color = MaterialTheme.colorScheme.outline)
                }
            }

            items(results, key = { it.uid }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUserClick(user.uid) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(user.avatarUrl, 44)

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text("@${user.username}", fontWeight = FontWeight.Bold)
                        Text(
                            user.email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    vm: MainViewModel,
    profileUid: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val profileUser by vm.profileUser.collectAsState()
    val profilePosts by vm.profilePosts.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val profileState by vm.profileState.collectAsState()
    val profileBlocked by vm.profileBlocked.collectAsState()
    val isFollowing by vm.isFollowing.collectAsState()
    val userReactions by vm.userReactions.collectAsState()

    var editUsername by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var repostPost by remember { mutableStateOf<Post?>(null) }

    val isMe = profileUid == currentUser?.uid
    val snackbarHost = remember { SnackbarHostState() }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        avatarUri = uri
    } // ex2

    LaunchedEffect(profileUid) {
        vm.loadUserProfile(profileUid)
        vm.listenPostsByUser(profileUid)
    }

    LaunchedEffect(profileUser) {
        editUsername = profileUser?.username ?: ""
    }

    LaunchedEffect(profilePosts) {
        if (profilePosts.isNotEmpty()) {
            vm.loadUserReactions(profilePosts.map { it.postId })
        }
    }

    LaunchedEffect(profileState) {
        when (profileState) {
            is UiState.Success -> {
                snackbarHost.showSnackbar("Done")
                avatarUri = null
                vm.resetProfileState()
            }

            is UiState.Error -> {
                snackbarHost.showSnackbar((profileState as UiState.Error).message)
                vm.resetProfileState()
            }

            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }

                Spacer(Modifier.weight(1f))

                Text("Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                Spacer(Modifier.weight(1f))
            }

            if (profileBlocked) {
                // ex9
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You cannot view this profile.")
                }
                return@Column
            }

            val user = profileUser

            if (user == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(avatarUri?.toString() ?: user.avatarUrl, 72)

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isMe) {
                        OutlinedTextField(
                            value = editUsername,
                            onValueChange = { editUsername = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "@${user.username}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            user.email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isMe) {
                Row {
                    TextButton(onClick = { avatarPicker.launch("image/*") }) {
                        Text("Change avatar") // ex2
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { vm.updateProfile(editUsername.trim(), avatarUri) },
                        enabled = profileState !is UiState.Loading && editUsername.isNotBlank()
                    ) {
                        Text("Save profile") // ex2
                    }
                }
            } else {
                Row {
                    Button(
                        onClick = { vm.followUser(profileUid) },
                        enabled = !isFollowing && profileState !is UiState.Loading
                    ) {
                        Text(if (isFollowing) "Following" else "Follow") // ex5
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(onClick = { vm.blockUser(profileUid) }) {
                        Text("Block") // ex9
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Posts", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (profilePosts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No posts", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profilePosts, key = { it.postId }) { post ->
                        PostItem(
                            post = post,
                            currentUserId = currentUser?.uid ?: "",
                            userReaction = userReactions[post.postId],
                            onReact = { emoji -> vm.react(post.postId, emoji) },
                            onCommentClick = { selectedPost = post },
                            onDeleteClick = { vm.deletePost(post.postId) }, // ex4
                            onRepostClick = { repostPost = post }, // ex6
                            onOpenProfile = { onOpenProfile(post.authorId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selectedPost?.let { post ->
        CommentSheet(
            post = post,
            vm = vm,
            onDismiss = { selectedPost = null }
        )
    }

    repostPost?.let { post ->
        RepostDialog(
            post = post,
            onDismiss = { repostPost = null },
            onRepost = { text ->
                vm.repost(text, post)
                repostPost = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    post: Post,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    val comments by vm.comments.collectAsState()
    val commentState by vm.commentState.collectAsState()

    var text by remember { mutableStateOf("") }

    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(post.postId) {
        vm.listenComments(post.postId)
    }

    LaunchedEffect(commentState) {
        when (commentState) {
            is UiState.Success -> {
                text = ""
                vm.resetCommentState()
            }

            is UiState.Error -> {
                snackbarHost.showSnackbar((commentState as UiState.Error).message)
                vm.resetCommentState()
            }

            else -> Unit
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            modifier = Modifier.fillMaxHeight(0.75f)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    text = "Bình luận (${post.commentsCount})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                HorizontalDivider()

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Chưa có bình luận nào",
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        items(comments, key = { it.commentId }) { comment ->
                            CommentItem(comment, fmt)
                            HorizontalDivider()
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Viết bình luận...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )

                    Spacer(Modifier.width(8.dp))

                    val isLoading = commentState is UiState.Loading

                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                vm.addComment(post.postId, text)
                            }
                        },
                        enabled = !isLoading && text.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Gửi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    fmt: SimpleDateFormat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "@${comment.authorUsername}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = comment.createdAt?.toDate()?.let { fmt.format(it) } ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(Modifier.height(3.dp))

        Text(comment.content, fontSize = 14.sp)
    }
}

@Composable
fun PostItem(
    post: Post,
    currentUserId: String,
    userReaction: String?,
    onReact: (String) -> Unit,
    onCommentClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRepostClick: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    var showPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) } // ex4

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorAvatarUrl, 36)

            Spacer(Modifier.width(8.dp))

            Text(
                "@${post.authorUsername}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onOpenProfile() } // ex3 + ex8
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(post.content, fontSize = 15.sp)

        // ex1
        post.imageUrls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = "Post image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(top = 8.dp)
            )
        }

        // ex7
        if (post.locationUrl.isNotBlank()) {
            TextButton(onClick = { openUrl(context, post.locationUrl) }) {
                Text("Open location in Google Maps")
            }
        }

        // ex6
        if (post.repostFromPostId.isNotBlank()) {
            RepostPreview(post = post)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            for (entry in post.reactions.filter { it.value > 0 }.entries.sortedByDescending { it.value }) {
                ReactionChip(
                    emoji = entry.key,
                    count = entry.value,
                    isSelected = userReaction == entry.key,
                    onClick = {
                        onReact(entry.key)
                        showPicker = false
                    }
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { showPicker = !showPicker }
            ) {
                Text(
                    text = if (showPicker) "✕" else "＋",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onCommentClick) {
                Text(
                    "💬 ${post.commentsCount}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            TextButton(onClick = onRepostClick) {
                Text("Repost", fontSize = 13.sp) // ex6
            }

            if (post.authorId == currentUserId) {
                TextButton(onClick = { showDeleteDialog = true }) {
                    Text("Delete", fontSize = 13.sp) // ex4
                }
            }
        }

        if (showPicker) {
            Spacer(Modifier.height(8.dp))

            Row {
                for (emoji in REACTION_LIST) {
                    val isSelected = userReaction == emoji

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.3f else 1f,
                        animationSpec = spring(),
                        label = "picker_$emoji"
                    )

                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .scale(scale)
                            .padding(end = 8.dp)
                            .clickable {
                                onReact(emoji)
                                showPicker = false
                            }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = post.createdAt?.toDate()?.let { fmt.format(it) } ?: "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete post?") },
            text = { Text("Are you sure you want to delete this post?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RepostPreview(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Original post by @${post.repostFromUsername}",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(post.repostFromContent)

            post.repostFromImageUrls.forEach { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Original post image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RepostDialog(
    post: Post,
    onDismiss: () -> Unit,
    onRepost: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repost") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Add your thoughts") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Text("Original: @${post.authorUsername}", fontWeight = FontWeight.Bold)
                Text(post.content, maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onRepost(text) }) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ReactionChip(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(),
        label = "scale_$emoji"
    )

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .scale(scale)
            .padding(end = 6.dp)
            .clickable { onClick() }
    ) {
        Text(
            text = "$emoji $count",
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Avatar(
    url: String,
    sizeDp: Int
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(sizeDp.dp)
    ) {
        if (url.isBlank()) {
            Box(contentAlignment = Alignment.Center) {
                Text("👤", fontSize = (sizeDp / 2).sp)
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun openUrl(
    context: Context,
    url: String
) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}