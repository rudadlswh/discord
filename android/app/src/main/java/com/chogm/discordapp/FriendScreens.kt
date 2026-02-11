package com.chogm.discordapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

private enum class HomeTab {
    HOME,
    NOTIFICATIONS,
    PROFILE
}

private data class DirectMessage(
    val initials: String,
    val name: String,
    val preview: String,
    val time: String,
    val highlight: Boolean
)

private data class DirectMessagePreview(
    val id: String,
    val senderId: String,
    val content: String,
    val createdAt: String
)

private data class DirectThread(
    val channelId: String,
    val friendId: String,
    val friendUsername: String,
    val friendDisplayName: String,
    val lastMessage: DirectMessagePreview?
)

private data class ChatMessage(
    val id: String,
    val senderId: String,
    val content: String,
    val createdAt: String
)

private data class LookupUser(
    val id: String,
    val username: String,
    val displayName: String
)

private data class FriendSummary(
    val id: String,
    val username: String,
    val displayName: String
)

private data class PendingFriendRequest(
    val id: String,
    val requesterId: String,
    val displayName: String,
    val username: String
)

private const val MAX_MESSAGE_BYTES = 4000
private const val CALL_REQUEST_PREFIX = "[CALL_REQUEST]"
private const val CALL_REQUEST_LABEL = "음성채팅 요청"

private data class CallRequestMessage(
    val callId: String,
    val label: String
)

private fun displayInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isBlank()) {
        return "?"
    }
    return trimmed.first().uppercaseChar().toString()
}

private fun parseCallRequestMessage(content: String): CallRequestMessage? {
    if (!content.startsWith(CALL_REQUEST_PREFIX)) {
        return null
    }
    val payload = content.removePrefix(CALL_REQUEST_PREFIX)
    val parts = payload.split("|", limit = 2)
    val callId = parts.getOrNull(0)?.trim().orEmpty()
    if (callId.isBlank()) {
        return null
    }
    val label = parts.getOrNull(1)?.trim().orEmpty()
    return CallRequestMessage(
        callId = callId,
        label = if (label.isBlank()) CALL_REQUEST_LABEL else label
    )
}

@Composable
private fun CallPanel(
    state: CallState,
    friendName: String,
    selfName: String,
    isOutgoing: Boolean,
    isMicMuted: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = when (state) {
        CallState.INCOMING -> "통화 요청"
        CallState.OUTGOING -> "연결 중..."
        CallState.CONNECTING -> "연결 중..."
        CallState.CONNECTED -> "통화 중"
        CallState.IDLE -> ""
    }
    val callerName = if (isOutgoing) selfName else friendName
    val receiverName = if (isOutgoing) friendName else selfName

    AppCard(modifier = modifier, background = DiscordColors.DarkSurfaceAlt) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "음성 채팅",
                color = DiscordColors.TextPrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = status,
                color = DiscordColors.TextSecondaryDark,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "발신자",
                        color = DiscordColors.TextMutedDark,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AvatarCircle(
                        text = displayInitial(callerName),
                        size = 56,
                        background = DiscordColors.AccentBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = callerName,
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "수신자",
                        color = DiscordColors.TextMutedDark,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AvatarCircle(
                        text = displayInitial(receiverName),
                        size = 56,
                        background = DiscordColors.DarkSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = receiverName,
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(DiscordColors.DarkSurface, RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleMic)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isMicMuted) Color.Red else DiscordColors.AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMicMuted) "마이크 꺼짐" else "마이크 켜짐",
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state == CallState.INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrimaryButton(
                        text = "수락",
                        enabled = true,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "거절",
                        enabled = true,
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (state != CallState.IDLE) {
                SecondaryButton(
                    text = "통화 종료",
                    enabled = true,
                    onClick = onEnd,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendHomeScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(HomeTab.HOME) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showAddFriendSheet by remember { mutableStateOf(false) }
    var showComposeSheet by remember { mutableStateOf(false) }
    var showFriendsSheet by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<FriendSummary?>(null) }
    var reopenFriendsOnClose by remember { mutableStateOf(false) }
    var activeThread by remember { mutableStateOf<DirectThread?>(null) }
    var pendingRequests by remember { mutableStateOf<List<PendingFriendRequest>>(emptyList()) }
    var requestsLoading by remember { mutableStateOf(false) }
    var requestsError by remember { mutableStateOf<String?>(null) }
    var requestsStatus by remember { mutableStateOf<String?>(null) }
    var friendsList by remember { mutableStateOf<List<FriendSummary>>(emptyList()) }
    var friendsLoading by remember { mutableStateOf(false) }
    var friendsError by remember { mutableStateOf<String?>(null) }
    var directThreads by remember { mutableStateOf<List<DirectThread>>(emptyList()) }
    var threadsLoading by remember { mutableStateOf(false) }
    var threadsError by remember { mutableStateOf<String?>(null) }
    suspend fun fetchAndUpdateRequests(showLoading: Boolean) {
        if (showLoading) {
            requestsLoading = true
        }
        val result = fetchPendingFriendRequests(context)
        pendingRequests = result.requests
        requestsError = result.errorMessage
        if (showLoading) {
            requestsLoading = false
        }
        if (result.errorMessage == null) {
            val userId = AppPrefs.getUserId(context)
            val seen = AppPrefs.getSeenFriendRequestIds(context, userId)
            val newRequests = result.requests.filter { it.id !in seen }
            if (newRequests.isNotEmpty()) {
                newRequests.forEach {
                    FriendNotifications.notifyNewRequest(context, it.displayName, it.id)
                }
                AppPrefs.addSeenFriendRequestIds(
                    context,
                    userId,
                    newRequests.map { it.id }.toSet()
                )
            }
        }
    }

    suspend fun fetchFriends(showLoading: Boolean) {
        if (showLoading) {
            friendsLoading = true
        }
        val result = fetchFriendsList(context)
        friendsList = result.friends
        friendsError = result.errorMessage
        if (showLoading) {
            friendsLoading = false
        }
    }

    suspend fun fetchAndUpdateThreads(showLoading: Boolean) {
        if (showLoading) {
            threadsLoading = true
        }
        val result = fetchDirectThreads(context)
        directThreads = result.threads
        threadsError = result.errorMessage
        if (showLoading) {
            threadsLoading = false
        }
        if (result.errorMessage == null) {
            val userId = AppPrefs.getUserId(context)
            val seen = AppPrefs.getSeenMessageIds(context, userId)
            val newMessages = result.threads.mapNotNull { thread ->
                val last = thread.lastMessage ?: return@mapNotNull null
                if (!userId.isNullOrBlank() && last.senderId == userId) return@mapNotNull null
                if (seen.contains(last.id)) return@mapNotNull null
                thread to last
            }

            if (newMessages.isNotEmpty()) {
                newMessages.forEach { (thread, message) ->
                    MessageNotifications.notifyNewMessage(
                        context,
                        thread.friendDisplayName,
                        message.content.take(80),
                        message.id
                    )
                }
                AppPrefs.addSeenMessageIds(
                    context,
                    userId,
                    newMessages.map { it.second.id }.toSet()
                )
            }
        }
    }

    fun openFriendProfile(friend: FriendSummary) {
        reopenFriendsOnClose = showFriendsSheet
        showFriendsSheet = false
        selectedFriend = friend
    }

    fun closeFriendProfile() {
        selectedFriend = null
        if (reopenFriendsOnClose) {
            showFriendsSheet = true
        }
        reopenFriendsOnClose = false
    }

    fun respondToRequest(request: PendingFriendRequest, accept: Boolean) {
        val token = AppPrefs.getToken(context)
        if (token.isNullOrBlank()) {
            requestsError = context.getString(R.string.add_friend_requires_login)
            return
        }
        val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
        val action = if (accept) "accept" else "reject"

        scope.launch {
            requestsError = null
            requestsStatus = null
            val response = withContext(Dispatchers.IO) {
                try {
                    ApiClient.executeRequest(
                        method = "POST",
                        url = ApiClient.buildUrl(baseUrl, "/api/friends/requests/${request.id}/$action"),
                        token = token
                    )
                } catch (ex: Exception) {
                    null
                }
            }

            if (response == null) {
                requestsError = "ERROR: Unknown error"
                return@launch
            }

            if (response.code in 200..299) {
                pendingRequests = pendingRequests.filterNot { it.id == request.id }
                val messageRes = if (accept) {
                    R.string.friend_requests_accepted
                } else {
                    R.string.friend_requests_rejected
                }
                requestsStatus = context.getString(messageRes, request.displayName)
                if (showFriendsSheet) {
                    fetchFriends(true)
                }
            } else {
                requestsError = "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchAndUpdateRequests(true)
        while (true) {
            delay(15000)
            fetchAndUpdateRequests(false)
        }
    }

    LaunchedEffect(Unit) {
        fetchAndUpdateThreads(true)
        while (true) {
            delay(8000)
            fetchAndUpdateThreads(false)
        }
    }

    LaunchedEffect(showFriendsSheet) {
        if (showFriendsSheet) {
            fetchFriends(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    HomeTab.HOME -> MessagesScreen(
                        threads = directThreads,
                        isLoading = threadsLoading,
                        errorMessage = threadsError,
                        currentUserId = AppPrefs.getUserId(context),
                        onAddFriend = { showAddFriendSheet = true },
                        onComposeMessage = { showComposeSheet = true },
                        onThreadSelected = { activeThread = it }
                    )
                    HomeTab.NOTIFICATIONS -> NotificationsScreen(
                        pendingRequests = pendingRequests,
                        isLoading = requestsLoading,
                        errorMessage = requestsError,
                        statusMessage = requestsStatus,
                        onAccept = { respondToRequest(it, true) },
                        onReject = { respondToRequest(it, false) },
                        onOpenMenu = { showNotificationsSheet = true }
                    )
                    HomeTab.PROFILE -> ProfileScreen(
                        onLogout = onLogout,
                        onShowFriends = { showFriendsSheet = true }
                    )
                }
            }

            HorizontalDivider(color = DiscordColors.DarkBorder, thickness = 1.dp)

            BottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        }

        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false },
                containerColor = DiscordColors.SheetBackground,
                dragHandle = { SheetHandle() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                NotificationsSheet()
            }
        }

        if (showAddFriendSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddFriendSheet = false },
                containerColor = DiscordColors.SheetBackground,
                dragHandle = { SheetHandle() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AddFriendSheet()
            }
        }

        if (showComposeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showComposeSheet = false },
                containerColor = DiscordColors.SheetBackground,
                dragHandle = { SheetHandle() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ComposeMessageSheet(
                    onDismiss = { showComposeSheet = false },
                    onMessageSent = {
                        scope.launch {
                            fetchAndUpdateThreads(true)
                        }
                    }
                )
            }
        }

        if (showFriendsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFriendsSheet = false },
                containerColor = DiscordColors.SheetBackground,
                dragHandle = { SheetHandle() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                FriendsSheet(
                    friends = friendsList,
                    isLoading = friendsLoading,
                    errorMessage = friendsError,
                    onFriendSelected = { openFriendProfile(it) }
                )
            }
        }

        if (selectedFriend != null) {
            ModalBottomSheet(
                onDismissRequest = { closeFriendProfile() },
                containerColor = DiscordColors.SheetBackground,
                dragHandle = { SheetHandle() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                FriendProfileSheet(
                    friend = selectedFriend!!,
                    onClose = { closeFriendProfile() }
                )
            }
        }

        if (activeThread != null) {
            DirectChatScreen(
                thread = activeThread!!,
                onBack = {
                    activeThread = null
                    scope.launch { fetchAndUpdateThreads(true) }
                }
            )
        }
    }
}

@Composable
private fun MessagesScreen(
    threads: List<DirectThread>,
    isLoading: Boolean,
    errorMessage: String?,
    currentUserId: String?,
    onAddFriend: () -> Unit,
    onComposeMessage: () -> Unit,
    onThreadSelected: (DirectThread) -> Unit
) {
    val context = LocalContext.current
    val messageItems = threads.map { thread ->
        val lastMessage = thread.lastMessage
        val preview = if (lastMessage != null) {
            if (!currentUserId.isNullOrBlank() && lastMessage.senderId == currentUserId) {
                context.getString(R.string.messages_compose_preview, lastMessage.content)
            } else {
                "${thread.friendDisplayName}: ${lastMessage.content}"
            }
        } else {
            context.getString(R.string.messages_no_threads)
        }
        val time = lastMessage?.createdAt?.let { formatRelativeTime(context, it) } ?: ""
        val initial = thread.friendDisplayName.trim().take(1)
            .ifEmpty { thread.friendUsername.take(1) }
        thread to DirectMessage(
            initials = initial,
            name = thread.friendDisplayName,
            preview = preview,
            time = time,
            highlight = lastMessage != null && lastMessage.senderId != currentUserId
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ServerRail()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.messages_title),
                    color = DiscordColors.TextPrimaryDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                SearchBar(placeholder = stringResource(id = R.string.messages_search_hint))

                AddFriendPill(onClick = onAddFriend)

                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    AvatarCircle("N", 46, DiscordColors.AccentBlue)
                    Spacer(modifier = Modifier.width(10.dp))
                    AvatarCircle("T", 46, DiscordColors.DarkSurfaceAlt)
                    Spacer(modifier = Modifier.width(10.dp))
                    AvatarCircle("D", 46, DiscordColors.DarkSurfaceAlt)
                    Spacer(modifier = Modifier.width(10.dp))
                    AvatarCircle("K", 46, DiscordColors.DarkSurfaceAlt)
                }

                Text(
                    text = stringResource(id = R.string.messages_section),
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp)
                )

                if (isLoading) {
                    Text(
                        text = stringResource(id = R.string.messages_threads_loading),
                        color = DiscordColors.TextMutedDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (messageItems.isEmpty() && !isLoading && errorMessage.isNullOrBlank()) {
                    Text(
                        text = stringResource(id = R.string.messages_no_threads),
                        color = DiscordColors.TextSecondaryDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Column(modifier = Modifier.padding(top = 12.dp)) {
                    messageItems.forEach { (thread, message) ->
                        MessageRow(message) { onThreadSelected(thread) }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onComposeMessage,
            containerColor = DiscordColors.AccentBlue,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_edit),
                contentDescription = stringResource(id = R.string.messages_compose),
                tint = DiscordColors.TextPrimaryDark
            )
        }
    }
}

@Composable
private fun ServerRail() {
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxSize()
            .background(DiscordColors.DarkRail)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarCircle("D", 44, DiscordColors.AccentBlue)
        Spacer(modifier = Modifier.height(14.dp))
        AvatarCircle("k", 44, DiscordColors.DarkSurfaceAlt)
        Spacer(modifier = Modifier.height(12.dp))
        AvatarCircle("z", 44, DiscordColors.DarkSurfaceAlt)
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(DiscordColors.DarkSurfaceAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = stringResource(id = R.string.server_add),
                tint = DiscordColors.AccentBlue
            )
        }
    }
}

@Composable
private fun SearchBar(placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(top = 12.dp)
            .background(DiscordColors.DarkSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = placeholder,
            tint = DiscordColors.TextMutedDark,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = placeholder,
            color = DiscordColors.TextMutedDark,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun AddFriendPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .background(DiscordColors.DarkSurfaceAlt, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus),
            contentDescription = stringResource(id = R.string.messages_add_friend),
            tint = DiscordColors.AccentBlue,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(id = R.string.messages_add_friend),
            color = DiscordColors.TextPrimaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun MessageRow(message: DirectMessage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(
            message.initials,
            44,
            if (message.highlight) DiscordColors.AccentBlue else DiscordColors.DarkSurfaceAlt
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = message.name,
                color = DiscordColors.TextPrimaryDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message.preview,
                color = DiscordColors.TextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Text(
            text = message.time,
            color = DiscordColors.TextMutedDark,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun NotificationsScreen(
    pendingRequests: List<PendingFriendRequest>,
    isLoading: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onAccept: (PendingFriendRequest) -> Unit,
    onReject: (PendingFriendRequest) -> Unit,
    onOpenMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(DiscordColors.DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.notifications_title),
                color = DiscordColors.TextPrimaryDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onOpenMenu) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(DiscordColors.DarkSurfaceAlt, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_vert),
                        contentDescription = stringResource(id = R.string.notifications_menu),
                        tint = DiscordColors.TextSecondaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = stringResource(id = R.string.friend_requests_title),
            color = DiscordColors.TextSecondaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )

        if (isLoading) {
            Text(
                text = stringResource(id = R.string.friend_requests_loading),
                color = DiscordColors.TextMutedDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage ?: "",
                color = DiscordColors.AccentBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (pendingRequests.isEmpty() && !isLoading) {
            AppCard(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = stringResource(id = R.string.friend_requests_empty),
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                pendingRequests.forEach { request ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val initial = request.displayName.trim().take(1)
                                .ifEmpty { request.username.take(1) }
                            AvatarCircle(initial, 38, DiscordColors.AccentBlue)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                            ) {
                                Text(
                                    text = request.displayName,
                                    color = DiscordColors.TextPrimaryDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${request.username}",
                                    color = DiscordColors.TextSecondaryDark,
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RequestActionButton(
                                    text = stringResource(id = R.string.friend_requests_accept),
                                    background = DiscordColors.AccentBlue,
                                    textColor = DiscordColors.TextPrimaryDark
                                ) { onAccept(request) }
                                RequestActionButton(
                                    text = stringResource(id = R.string.friend_requests_reject),
                                    background = DiscordColors.DarkSurfaceAlt,
                                    textColor = DiscordColors.TextPrimaryDark
                                ) { onReject(request) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        AppCard(modifier = Modifier.padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(DiscordColors.AccentBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_message),
                        contentDescription = stringResource(id = R.string.notifications_message),
                        tint = DiscordColors.TextPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.notifications_message),
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.notifications_message_subtitle),
                        color = DiscordColors.TextSecondaryDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = stringResource(id = R.string.notifications_message_time),
                    color = DiscordColors.TextMutedDark,
                    fontSize = 11.sp
                )
            }
        }

        Text(
            text = stringResource(id = R.string.notifications_recommended),
            color = DiscordColors.TextSecondaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp)
        )

        AppCard(modifier = Modifier.padding(top = 12.dp)) {
            Column {
                RecommendedFriendRow(
                    initial = "B",
                    name = stringResource(id = R.string.notifications_friend_1),
                    tag = stringResource(id = R.string.notifications_friend_1_tag),
                    highlight = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                RecommendedFriendRow(
                    initial = "A",
                    name = stringResource(id = R.string.notifications_friend_2),
                    tag = stringResource(id = R.string.notifications_friend_2_tag),
                    highlight = false
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.notifications_show_all),
                color = DiscordColors.AccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = stringResource(id = R.string.notifications_show_all),
                tint = DiscordColors.TextSecondaryDark,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RecommendedFriendRow(initial: String, name: String, tag: String, highlight: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(initial, 38, if (highlight) DiscordColors.AccentBlue else DiscordColors.DarkSurfaceAlt)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(text = name, color = DiscordColors.TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = tag, color = DiscordColors.TextSecondaryDark, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(DiscordColors.DarkSurfaceAlt, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.notifications_add),
                color = DiscordColors.TextPrimaryDark,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RequestActionButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProfileScreen(onLogout: () -> Unit, onShowFriends: () -> Unit) {
    val context = LocalContext.current
    val displayName = AppPrefs.getDisplayName(context) ?: stringResource(id = R.string.profile_default_name)
    val username = AppPrefs.getUsername(context) ?: stringResource(id = R.string.profile_default_username)
    val initial = displayName.trim().take(1).ifEmpty { "D" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(DiscordColors.DarkBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(DiscordColors.ProfileHeaderStart, DiscordColors.ProfileHeaderEnd)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBubble(icon = painterResource(id = R.drawable.ic_badge))
                IconBubble(icon = painterResource(id = R.drawable.ic_trophy))
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(DiscordColors.DarkSurfaceAlt, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.profile_nitro),
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconBubble(icon = painterResource(id = R.drawable.ic_settings))
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(initial, 72, DiscordColors.DarkSurfaceAlt)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = displayName,
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@$username",
                        color = DiscordColors.TextSecondaryDark,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            AppCard(modifier = Modifier.padding(top = 14.dp)) {
                Text(
                    text = stringResource(id = R.string.profile_status_placeholder),
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 16.dp)
                    .background(DiscordColors.DarkSurfaceAlt, RoundedCornerShape(14.dp))
                    .clickable { }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = stringResource(id = R.string.profile_edit),
                        tint = DiscordColors.TextPrimaryDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.profile_edit),
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            AppCard(modifier = Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_calendar),
                        contentDescription = stringResource(id = R.string.profile_joined),
                        tint = DiscordColors.TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            text = stringResource(id = R.string.profile_joined),
                            color = DiscordColors.TextSecondaryDark,
                            fontSize = 11.sp
                        )
                        Text(
                            text = stringResource(id = R.string.profile_joined_date),
                            color = DiscordColors.TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            SectionHeader(
                text = stringResource(id = R.string.profile_connections),
                modifier = Modifier.padding(top = 18.dp)
            )

            AppCard(modifier = Modifier.padding(top = 10.dp)) {
                Column {
                    ConnectionRow(stringResource(id = R.string.profile_connection_1))
                    Spacer(modifier = Modifier.height(10.dp))
                    ConnectionRow(stringResource(id = R.string.profile_connection_2))
                }
            }

            SectionHeader(
                text = stringResource(id = R.string.profile_friends),
                modifier = Modifier.padding(top = 18.dp)
            )

            AppCard(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onShowFriends)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle("J", 32, DiscordColors.AccentBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    AvatarCircle("K", 32, DiscordColors.DarkSurfaceAlt)
                    Spacer(modifier = Modifier.width(6.dp))
                    AvatarCircle("D", 32, DiscordColors.DarkSurfaceAlt)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = stringResource(id = R.string.profile_friends),
                        tint = DiscordColors.TextSecondaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            SectionHeader(
                text = stringResource(id = R.string.profile_notes),
                modifier = Modifier.padding(top = 18.dp)
            )

            AppCard(modifier = Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = stringResource(id = R.string.profile_notes),
                        tint = DiscordColors.TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.profile_notes_placeholder),
                        color = DiscordColors.TextSecondaryDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 16.dp)
                    .background(DiscordColors.DarkSurfaceAlt, RoundedCornerShape(14.dp))
                    .clickable(onClick = onLogout),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.logout_button),
                    color = Color.Red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = R.drawable.ic_link),
            contentDescription = title,
            tint = DiscordColors.TextSecondaryDark,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = DiscordColors.TextPrimaryDark,
            fontSize = 13.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = title,
            tint = DiscordColors.TextSecondaryDark,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun BottomNav(selectedTab: HomeTab, onTabSelected: (HomeTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(DiscordColors.NavBackground)
    ) {
        BottomNavItem(
            text = stringResource(id = R.string.nav_home),
            icon = R.drawable.ic_nav_message,
            selected = selectedTab == HomeTab.HOME,
            onClick = { onTabSelected(HomeTab.HOME) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            text = stringResource(id = R.string.nav_notifications),
            icon = R.drawable.ic_nav_bell,
            selected = selectedTab == HomeTab.NOTIFICATIONS,
            onClick = { onTabSelected(HomeTab.NOTIFICATIONS) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            text = stringResource(id = R.string.nav_profile),
            icon = R.drawable.ic_nav_profile,
            selected = selectedTab == HomeTab.PROFILE,
            onClick = { onTabSelected(HomeTab.PROFILE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BottomNavItem(
    text: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) DiscordColors.NavActive else DiscordColors.NavInactive

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .padding(top = 4.dp)
                .background(if (selected) tint else Color.Transparent, CircleShape)
        )
        Text(
            text = text,
            color = tint,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .background(DiscordColors.DarkBorder, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun NotificationsSheet() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.notifications_sheet_title),
            color = DiscordColors.TextPrimaryDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        AppCard(modifier = Modifier.padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle("@", 32, DiscordColors.DarkSurfaceAlt)
                Text(
                    text = stringResource(id = R.string.notifications_sheet_role_mentions),
                    color = DiscordColors.TextPrimaryDark,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(DiscordColors.AccentBlue, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ON", color = DiscordColors.TextPrimaryDark, fontSize = 10.sp)
                }
            }
        }

        AppCard(modifier = Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle("@", 32, DiscordColors.DarkSurfaceAlt)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.notifications_sheet_indirect_mentions),
                        color = DiscordColors.TextPrimaryDark,
                        fontSize = 13.sp
                    )
                    Text(
                        text = stringResource(id = R.string.notifications_sheet_indirect_subtitle),
                        color = DiscordColors.TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(DiscordColors.AccentBlue, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ON", color = DiscordColors.TextPrimaryDark, fontSize = 10.sp)
                }
            }
        }

        AppCard(modifier = Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = stringResource(id = R.string.notifications_sheet_settings),
                    tint = DiscordColors.TextSecondaryDark,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(id = R.string.notifications_sheet_settings),
                    color = DiscordColors.TextPrimaryDark,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = stringResource(id = R.string.notifications_sheet_settings),
                    tint = DiscordColors.TextSecondaryDark,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AddFriendSheet() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var foundUser by remember { mutableStateOf<LookupUser?>(null) }
    var requestSent by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.add_friend_title),
            color = DiscordColors.TextPrimaryDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(id = R.string.add_friend_subtitle),
            color = DiscordColors.TextSecondaryDark,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        DiscordTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(id = R.string.add_friend_query_hint),
            modifier = Modifier.padding(top = 14.dp),
            containerColor = DiscordColors.DarkSurface,
            textColor = DiscordColors.TextPrimaryDark,
            placeholderColor = DiscordColors.TextMutedDark
        )

        SecondaryButton(
            text = if (isLoading) stringResource(id = R.string.add_friend_searching)
            else stringResource(id = R.string.add_friend_find_button),
            enabled = !isLoading,
            onClick = {
                val trimmed = query.trim()
                if (trimmed.isBlank()) {
                    errorMessage = context.getString(R.string.add_friend_missing_query)
                    statusMessage = null
                    return@SecondaryButton
                }

                val token = AppPrefs.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = context.getString(R.string.add_friend_requires_login)
                    statusMessage = null
                    return@SecondaryButton
                }

                val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
                isLoading = true
                errorMessage = null
                statusMessage = null
                foundUser = null
                requestSent = false

                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        try {
                            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                            val url = ApiClient.buildUrl(baseUrl, "/api/users/lookup") + "?query=$encodedQuery"
                            ApiClient.executeRequest("GET", url, token = token)
                        } catch (ex: Exception) {
                            null
                        }
                    }

                    isLoading = false
                    if (response == null) {
                        errorMessage = "ERROR: Unknown error"
                        return@launch
                    }

                    if (response.code in 200..299) {
                        try {
                            val json = JSONObject(response.body)
                            val id = json.getString("id")
                            val username = json.getString("username")
                            val displayName = json.getString("displayName")

                            val currentUserId = AppPrefs.getUserId(context)
                            if (!currentUserId.isNullOrBlank() && currentUserId == id) {
                                errorMessage = context.getString(R.string.add_friend_self_error)
                                return@launch
                            }

                            foundUser = LookupUser(id, username, displayName)
                        } catch (ex: Exception) {
                            errorMessage = "ERROR: ${ex.message ?: "Invalid response"}"
                        }
                    } else {
                        errorMessage = "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
                    }
                }
            }
        )

        if (foundUser != null) {
            val user = foundUser!!
            AppCard(modifier = Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val initial = user.displayName.trim().take(1).ifEmpty { user.username.take(1) }
                    AvatarCircle(initial, 40, DiscordColors.AccentBlue)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = user.displayName,
                            color = DiscordColors.TextPrimaryDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${user.username}",
                            color = DiscordColors.TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            PrimaryButton(
                text = if (requestSent) stringResource(id = R.string.add_friend_sent_button)
                else stringResource(id = R.string.add_friend_send_button),
                enabled = !isLoading && !requestSent,
                onClick = {
                    val token = AppPrefs.getToken(context)
                    if (token.isNullOrBlank()) {
                        errorMessage = context.getString(R.string.add_friend_requires_login)
                        return@PrimaryButton
                    }
                    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))

                    isLoading = true
                    errorMessage = null
                    statusMessage = null

                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            try {
                                val body = JSONObject().put("addresseeId", user.id)
                                ApiClient.executeRequest(
                                    method = "POST",
                                    url = ApiClient.buildUrl(baseUrl, "/api/friends/requests"),
                                    jsonBody = body,
                                    token = token
                                )
                            } catch (ex: Exception) {
                                null
                            }
                        }

                        isLoading = false
                        if (response == null) {
                            errorMessage = "ERROR: Unknown error"
                            return@launch
                        }

                        if (response.code in 200..299) {
                            statusMessage = context.getString(
                                R.string.add_friend_sent,
                                user.displayName
                            )
                            requestSent = true
                        } else {
                            errorMessage = "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
                        }
                    }
                }
            )
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage ?: "",
                color = DiscordColors.AccentBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun formatRelativeTime(context: Context, createdAt: String): String {
    return try {
        val instant = Instant.parse(createdAt)
        val duration = Duration.between(instant, Instant.now()).coerceAtLeast(Duration.ZERO)
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        when {
            minutes < 1 -> context.getString(R.string.messages_time_now)
            hours < 1 -> context.getString(R.string.messages_time_minutes, minutes.toInt())
            days < 1 -> context.getString(R.string.messages_time_hours, hours.toInt())
            else -> context.getString(R.string.messages_time_days, days.toInt())
        }
    } catch (_: Exception) {
        context.getString(R.string.messages_time_now)
    }
}

@Composable
private fun ComposeMessageSheet(
    onDismiss: () -> Unit,
    onMessageSent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFriend by remember { mutableStateOf<FriendSummary?>(null) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = fetchFriendsList(context)
        friends = result.friends
        errorMessage = result.errorMessage
        isLoading = false
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.messages_compose_title),
                color = DiscordColors.TextPrimaryDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(id = R.string.messages_compose_close),
                color = DiscordColors.TextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }

        Text(
            text = stringResource(id = R.string.messages_compose_select_friend),
            color = DiscordColors.TextSecondaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (isLoading) {
            Text(
                text = stringResource(id = R.string.messages_compose_loading),
                color = DiscordColors.TextMutedDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (friends.isEmpty() && !isLoading) {
            AppCard(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = stringResource(id = R.string.messages_compose_empty_friends),
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                friends.forEach { friend ->
                    val isSelected = selectedFriend?.id == friend.id
                    AppCard(
                        modifier = Modifier
                            .clickable { selectedFriend = friend },
                        background = if (isSelected) DiscordColors.DarkSurfaceAlt else DiscordColors.DarkCard
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val initial = friend.displayName.trim().take(1)
                                .ifEmpty { friend.username.take(1) }
                            AvatarCircle(initial, 36, DiscordColors.AccentBlue)
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    text = friend.displayName,
                                    color = DiscordColors.TextPrimaryDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${friend.username}",
                                    color = DiscordColors.TextSecondaryDark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Text(
            text = stringResource(id = R.string.messages_compose_message_label),
            color = DiscordColors.TextSecondaryDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp)
        )

        DiscordTextField(
            value = message,
            onValueChange = { message = it },
            placeholder = stringResource(id = R.string.messages_compose_message_hint),
            modifier = Modifier.padding(top = 8.dp),
            containerColor = DiscordColors.DarkSurface,
            textColor = DiscordColors.TextPrimaryDark,
            placeholderColor = DiscordColors.TextMutedDark
        )

        PrimaryButton(
            text = if (isSending) stringResource(id = R.string.messages_compose_sending)
            else stringResource(id = R.string.messages_compose_send),
            enabled = !isLoading && !isSending,
            onClick = {
                val selected = selectedFriend
                val trimmed = message.trim()
                if (selected == null) {
                    errorMessage = context.getString(R.string.messages_compose_missing_friend)
                    return@PrimaryButton
                }
                if (trimmed.isBlank()) {
                    errorMessage = context.getString(R.string.messages_compose_missing_message)
                    return@PrimaryButton
                }
                if (trimmed.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
                    errorMessage = context.getString(R.string.chat_message_too_long)
                    return@PrimaryButton
                }
                scope.launch {
                    isSending = true
                    errorMessage = null
                    val error = sendDirectMessage(context, selected.id, trimmed)
                    isSending = false
                    if (error != null) {
                        errorMessage = error
                        return@launch
                    }
                    onMessageSent()
                    onDismiss()
                }
            }
        )
    }
}

@Composable
private fun DirectChatScreen(
    thread: DirectThread,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = AppPrefs.getUserId(context)
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var chatSocket by remember { mutableStateOf<ChatSocket?>(null) }
    val callState by CallManager.state.collectAsState()
    val activeCall by CallManager.activeCall.collectAsState()
    val isMicMuted by CallManager.isMicMuted.collectAsState()
    val scrollState = rememberScrollState()
    val isActiveCall = activeCall?.channelId == thread.channelId
    val stateForThread = if (isActiveCall) callState else CallState.IDLE
    val hasOtherCall = callState != CallState.IDLE && !isActiveCall

    suspend fun loadMessages(showLoading: Boolean) {
        if (showLoading) {
            isLoading = true
        }
        val result = fetchChannelMessages(context, thread.channelId)
        messages = result.messages
        errorMessage = result.errorMessage
        if (showLoading) {
            isLoading = false
        }
        val userId = AppPrefs.getUserId(context)
        if (result.errorMessage == null && !userId.isNullOrBlank()) {
            AppPrefs.addSeenMessageIds(
                context,
                userId,
                result.messages.map { it.id }.toSet()
            )
        }
    }

    fun sendCallRequestMessage() {
        val callId = CallManager.activeCall.value?.callId ?: return
        val content = "$CALL_REQUEST_PREFIX$callId|$CALL_REQUEST_LABEL"
        scope.launch {
            val socket = chatSocket
            val sent = socket?.sendMessage(content) ?: false
            val result = if (!sent) {
                sendChannelMessage(context, thread.channelId, content)
            } else {
                null
            }
            if (result != null) {
                if (result.errorMessage != null) {
                    errorMessage = result.errorMessage
                    return@launch
                }
                val message = result.message
                if (message != null && messages.none { it.id == message.id }) {
                    messages = messages + message
                    val userId = AppPrefs.getUserId(context)
                    if (!userId.isNullOrBlank()) {
                        AppPrefs.addSeenMessageIds(context, userId, setOf(message.id))
                    }
                }
            }
        }
    }

    fun ensureAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val activity = context as? Activity
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 1201)
        }
        errorMessage = "마이크 권한이 필요합니다."
        return false
    }

    fun joinCallFromMessage(callId: String) {
        if (!ensureAudioPermission()) {
            return
        }
        if (callState == CallState.IDLE) {
            CallManager.handleIncomingCall(
                context,
                callId,
                thread.channelId,
                thread.friendId,
                thread.friendDisplayName,
                notify = false
            )
        }
        CallManager.acceptIncomingCall(context)
    }

    fun startOutgoingCall() {
        if (callState != CallState.IDLE) {
            return
        }
        if (!ensureAudioPermission()) {
            return
        }
        CallManager.startOutgoingCall(
            context = context,
            channelId = thread.channelId,
            friendId = thread.friendId,
            friendName = thread.friendDisplayName
        )
        sendCallRequestMessage()
    }

    fun endCall() {
        CallManager.endCall(context)
    }

    fun acceptIncomingCall() {
        if (!ensureAudioPermission()) {
            return
        }
        CallManager.acceptIncomingCall(context)
    }

    fun rejectIncomingCall() {
        CallManager.rejectIncomingCall(context)
    }

    LaunchedEffect(thread.channelId) {
        loadMessages(true)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            withFrameNanos { }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    DisposableEffect(thread.channelId) {
        val token = AppPrefs.getToken(context)
        val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
        if (!token.isNullOrBlank()) {
            val socket = ChatSocket(
                baseUrl = baseUrl,
                token = token,
                channelId = thread.channelId,
                onMessage = { message ->
                    scope.launch {
                        if (messages.none { it.id == message.id }) {
                            messages = messages + message
                        }
                        val userId = AppPrefs.getUserId(context)
                        if (!userId.isNullOrBlank()) {
                            AppPrefs.addSeenMessageIds(context, userId, setOf(message.id))
                        }
                    }
                },
                onError = { error ->
                    scope.launch { errorMessage = error }
                }
            )
            socket.connect()
            chatSocket = socket
        }

        onDispose {
            chatSocket?.close()
            chatSocket = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.DarkBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .background(DiscordColors.DarkSurfaceAlt, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = DiscordColors.TextPrimaryDark,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(180f)
                )
            }
            Text(
                text = thread.friendDisplayName,
                color = DiscordColors.TextPrimaryDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (stateForThread == CallState.OUTGOING || stateForThread == CallState.CONNECTING) {
                CircularProgressIndicator(
                    color = DiscordColors.TextPrimaryDark,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(DiscordColors.DarkSurfaceAlt, CircleShape)
                    .clickable(enabled = stateForThread != CallState.INCOMING && !hasOtherCall) {
                        when (stateForThread) {
                            CallState.IDLE -> startOutgoingCall()
                            CallState.OUTGOING, CallState.CONNECTING, CallState.CONNECTED -> endCall()
                            CallState.INCOMING -> Unit
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (stateForThread == CallState.CONNECTED) Icons.Default.CallEnd else Icons.Default.Call,
                    contentDescription = null,
                    tint = if (stateForThread == CallState.INCOMING || hasOtherCall) {
                        DiscordColors.TextMutedDark
                    } else {
                        DiscordColors.TextPrimaryDark
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(color = DiscordColors.DarkBorder, thickness = 1.dp)

        if (stateForThread != CallState.IDLE) {
            val selfName = AppPrefs.getDisplayName(context)
                ?: AppPrefs.getUsername(context)
                ?: "나"
            CallPanel(
                state = stateForThread,
                friendName = thread.friendDisplayName,
                selfName = selfName,
                isOutgoing = activeCall?.isOutgoing ?: false,
                isMicMuted = isMicMuted,
                onAccept = { acceptIncomingCall() },
                onDecline = { rejectIncomingCall() },
                onEnd = { endCall() },
                onToggleMic = { CallManager.toggleMic() },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            if (isLoading) {
                Text(
                    text = stringResource(id = R.string.chat_loading),
                    color = DiscordColors.TextMutedDark,
                    fontSize = 12.sp
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (messages.isEmpty() && !isLoading && errorMessage.isNullOrBlank()) {
                Text(
                    text = stringResource(id = R.string.chat_empty),
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            }

            messages.forEach { message ->
                val isSelf = !currentUserId.isNullOrBlank() && message.senderId == currentUserId
                val callRequest = parseCallRequestMessage(message.content)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelf) DiscordColors.AccentBlue else DiscordColors.DarkSurfaceAlt,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (callRequest != null) {
                            Column {
                                Text(
                                    text = callRequest.label,
                                    color = DiscordColors.TextPrimaryDark,
                                    fontSize = 13.sp
                                )
                                if (!isSelf) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(
                                        onClick = { joinCallFromMessage(callRequest.callId) },
                                        enabled = callState == CallState.IDLE || callState == CallState.INCOMING
                                    ) {
                                        Text(text = "참가", color = DiscordColors.AccentBlue)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = message.content,
                                color = DiscordColors.TextPrimaryDark,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.chat_input_hint),
                        color = DiscordColors.TextMutedDark,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = DiscordColors.DarkSurface,
                    unfocusedContainerColor = DiscordColors.DarkSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = DiscordColors.AccentBlue
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .height(48.dp)
                    .background(DiscordColors.AccentBlue, RoundedCornerShape(14.dp))
                    .clickable(enabled = !isSending) {
                        val trimmed = input.trim()
                        if (trimmed.isBlank()) {
                            return@clickable
                        }
                        if (trimmed.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
                            errorMessage = context.getString(R.string.chat_message_too_long)
                            return@clickable
                        }
                        scope.launch {
                            isSending = true
                            val socket = chatSocket
                            val sent = socket?.sendMessage(trimmed) ?: false
                            val result = if (!sent) {
                                sendChannelMessage(context, thread.channelId, trimmed)
                            } else {
                                null
                            }
                            isSending = false
                            if (result != null) {
                                if (result.errorMessage != null) {
                                    errorMessage = result.errorMessage
                                    return@launch
                                }
                                val message = result.message
                                if (message != null && messages.none { it.id == message.id }) {
                                    messages = messages + message
                                    val userId = AppPrefs.getUserId(context)
                                    if (!userId.isNullOrBlank()) {
                                        AppPrefs.addSeenMessageIds(context, userId, setOf(message.id))
                                    }
                                }
                            }
                            input = ""
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSending) stringResource(id = R.string.chat_send_in_progress)
                    else stringResource(id = R.string.chat_send),
                    color = DiscordColors.TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (stateForThread == CallState.INCOMING) {
        AlertDialog(
            onDismissRequest = { rejectIncomingCall() },
            title = {
                Text(
                    text = "음성 통화",
                    color = DiscordColors.TextPrimaryDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${thread.friendDisplayName}님이 통화를 요청했어요.",
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { acceptIncomingCall() }) {
                    Text(text = "수락", color = DiscordColors.AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectIncomingCall() }) {
                    Text(text = "거절", color = Color.Red)
                }
            }
        )
    }
}

private class ChatSocket(
    private val baseUrl: String,
    private val token: String,
    private val channelId: String,
    private val onMessage: (ChatMessage) -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null

    fun connect() {
        val wsUrl = ApiClient.buildWebSocketUrl(baseUrl, "/ws/chat/$channelId")
        if (wsUrl.isNullOrBlank()) {
            onError("웹소켓 URL이 올바르지 않습니다.")
            return
        }
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError("웹소켓 오류: ${t.message ?: "Unknown error"}")
            }
        })
    }

    fun sendMessage(content: String): Boolean {
        val ws = webSocket ?: return false
        val payload = JSONObject().put("content", content).toString()
        return ws.send(payload)
    }

    fun close() {
        webSocket?.close(1000, "bye")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val error = json.optString("error").trim()
            if (error.isNotEmpty()) {
                onError(error)
                return
            }
            val id = json.optString("id")
            if (id.isNullOrBlank()) {
                return
            }
            val message = ChatMessage(
                id = id,
                senderId = json.optString("senderId"),
                content = json.optString("content"),
                createdAt = json.optString("createdAt")
            )
            onMessage(message)
        } catch (ex: Exception) {
            onError("웹소켓 메시지 파싱 실패: ${ex.message ?: "Unknown error"}")
        }
    }
}

@Composable
private fun FriendsSheet(
    friends: List<FriendSummary>,
    isLoading: Boolean,
    errorMessage: String?,
    onFriendSelected: (FriendSummary) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.friend_list_title),
            color = DiscordColors.TextPrimaryDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            Text(
                text = stringResource(id = R.string.friend_list_loading),
                color = DiscordColors.TextMutedDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (friends.isEmpty() && !isLoading) {
            AppCard(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = stringResource(id = R.string.friend_list_empty),
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                friends.forEach { friend ->
                    AppCard(
                        modifier = Modifier.clickable { onFriendSelected(friend) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val initial = friend.displayName.trim().take(1)
                                .ifEmpty { friend.username.take(1) }
                            AvatarCircle(initial, 38, DiscordColors.AccentBlue)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                            ) {
                                Text(
                                    text = friend.displayName,
                                    color = DiscordColors.TextPrimaryDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${friend.username}",
                                    color = DiscordColors.TextSecondaryDark,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = stringResource(id = R.string.friend_list_open_profile),
                                tint = DiscordColors.TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun FriendProfileSheet(
    friend: FriendSummary,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.friend_profile_title),
                color = DiscordColors.TextPrimaryDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(id = R.string.friend_profile_close),
                color = DiscordColors.TextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onClose)
            )
        }

        val initial = friend.displayName.trim().take(1).ifEmpty { friend.username.take(1) }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(initial, 64, DiscordColors.AccentBlue)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = friend.displayName,
                    color = DiscordColors.TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${friend.username}",
                    color = DiscordColors.TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        }

        AppCard(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                text = stringResource(id = R.string.friend_profile_status_placeholder),
                color = DiscordColors.TextSecondaryDark,
                fontSize = 12.sp
            )
        }

        SectionHeader(
            text = stringResource(id = R.string.profile_notes),
            modifier = Modifier.padding(top = 18.dp)
        )

        AppCard(modifier = Modifier.padding(top = 10.dp)) {
            Text(
                text = stringResource(id = R.string.profile_notes_placeholder),
                color = DiscordColors.TextSecondaryDark,
                fontSize = 12.sp
            )
        }
    }
}

private data class PendingRequestsResult(
    val requests: List<PendingFriendRequest>,
    val errorMessage: String?
)

private suspend fun fetchPendingFriendRequests(context: Context): PendingRequestsResult {
    val token = AppPrefs.getToken(context)
    if (token.isNullOrBlank()) {
        return PendingRequestsResult(
            emptyList(),
            context.getString(R.string.add_friend_requires_login)
        )
    }

    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
    return withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.executeRequest(
                method = "GET",
                url = ApiClient.buildUrl(baseUrl, "/api/friends/requests"),
                token = token
            )

            if (response.code !in 200..299) {
                val message = ApiClient.extractErrorMessage(response.body)
                return@withContext PendingRequestsResult(
                    emptyList(),
                    "ERROR ${response.code}: $message"
                )
            }

            val body = response.body.trim()
            if (body.isBlank()) {
                return@withContext PendingRequestsResult(emptyList(), null)
            }

            val array = JSONArray(body)
            val results = mutableListOf<PendingFriendRequest>()
            val cache = mutableMapOf<String, LookupUser?>()

            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val requestId = item.getString("id")
                val requesterId = item.getString("requesterId")
                val user = cache.getOrPut(requesterId) {
                    lookupUserById(baseUrl, token, requesterId)
                }
                val displayName = user?.displayName ?: requesterId.take(8)
                val username = user?.username ?: requesterId.take(8)
                results.add(
                    PendingFriendRequest(
                        id = requestId,
                        requesterId = requesterId,
                        displayName = displayName,
                        username = username
                    )
                )
            }

            PendingRequestsResult(results, null)
        } catch (ex: Exception) {
            PendingRequestsResult(emptyList(), "ERROR: ${ex.message ?: "Unknown error"}")
        }
    }
}

private data class FriendsListResult(
    val friends: List<FriendSummary>,
    val errorMessage: String?
)

private data class DirectThreadsResult(
    val threads: List<DirectThread>,
    val errorMessage: String?
)

private data class ChatMessagesResult(
    val messages: List<ChatMessage>,
    val errorMessage: String?
)

private data class ChatMessageSendResult(
    val message: ChatMessage?,
    val errorMessage: String?
)

private suspend fun fetchFriendsList(context: Context): FriendsListResult {
    val token = AppPrefs.getToken(context)
    if (token.isNullOrBlank()) {
        return FriendsListResult(
            emptyList(),
            context.getString(R.string.add_friend_requires_login)
        )
    }

    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
    return withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.executeRequest(
                method = "GET",
                url = ApiClient.buildUrl(baseUrl, "/api/friends"),
                token = token
            )

            if (response.code !in 200..299) {
                val message = ApiClient.extractErrorMessage(response.body)
                return@withContext FriendsListResult(
                    emptyList(),
                    "ERROR ${response.code}: $message"
                )
            }

            val body = response.body.trim()
            if (body.isBlank()) {
                return@withContext FriendsListResult(emptyList(), null)
            }

            val array = JSONArray(body)
            val results = mutableListOf<FriendSummary>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                results.add(
                    FriendSummary(
                        id = item.getString("id"),
                        username = item.getString("username"),
                        displayName = item.getString("displayName")
                    )
                )
            }

            FriendsListResult(results, null)
        } catch (ex: Exception) {
            FriendsListResult(emptyList(), "ERROR: ${ex.message ?: "Unknown error"}")
        }
    }
}

private suspend fun fetchDirectThreads(context: Context): DirectThreadsResult {
    val token = AppPrefs.getToken(context)
    if (token.isNullOrBlank()) {
        return DirectThreadsResult(
            emptyList(),
            context.getString(R.string.add_friend_requires_login)
        )
    }

    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
    return withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.executeRequest(
                method = "GET",
                url = ApiClient.buildUrl(baseUrl, "/api/dm/threads"),
                token = token
            )

            if (response.code !in 200..299) {
                val message = ApiClient.extractErrorMessage(response.body)
                return@withContext DirectThreadsResult(
                    emptyList(),
                    "ERROR ${response.code}: $message"
                )
            }

            val body = response.body.trim()
            if (body.isBlank()) {
                return@withContext DirectThreadsResult(emptyList(), null)
            }

            val array = JSONArray(body)
            val results = mutableListOf<DirectThread>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val lastMessageJson = item.optJSONObject("lastMessage")
                val lastMessage = if (lastMessageJson != null) {
                    DirectMessagePreview(
                        id = lastMessageJson.getString("id"),
                        senderId = lastMessageJson.getString("senderId"),
                        content = lastMessageJson.getString("content"),
                        createdAt = lastMessageJson.getString("createdAt")
                    )
                } else {
                    null
                }

                results.add(
                    DirectThread(
                        channelId = item.getString("channelId"),
                        friendId = item.getString("friendId"),
                        friendUsername = item.getString("friendUsername"),
                        friendDisplayName = item.getString("friendDisplayName"),
                        lastMessage = lastMessage
                    )
                )
            }

            DirectThreadsResult(results, null)
        } catch (ex: Exception) {
            DirectThreadsResult(emptyList(), "ERROR: ${ex.message ?: "Unknown error"}")
        }
    }
}

private suspend fun sendDirectMessage(
    context: Context,
    friendId: String,
    content: String
): String? {
    val token = AppPrefs.getToken(context)
    if (token.isNullOrBlank()) {
        return context.getString(R.string.add_friend_requires_login)
    }

    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
    return withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("toUserId", friendId)
                .put("content", content)
            val response = ApiClient.executeRequest(
                method = "POST",
                url = ApiClient.buildUrl(baseUrl, "/api/dm/send"),
                jsonBody = body,
                token = token
            )
            if (response.code in 200..299) {
                null
            } else {
                "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
            }
        } catch (ex: Exception) {
            "ERROR: ${ex.message ?: "Unknown error"}"
        }
    }
}

private suspend fun fetchChannelMessages(
    context: Context,
    channelId: String
): ChatMessagesResult {
    val token = AppPrefs.getToken(context)
    if (token.isNullOrBlank()) {
        return ChatMessagesResult(
            emptyList(),
            context.getString(R.string.add_friend_requires_login)
        )
    }

    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
    return withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.executeRequest(
                method = "GET",
                url = ApiClient.buildUrl(baseUrl, "/api/channels/$channelId/messages"),
                token = token
            )
            if (response.code !in 200..299) {
                val message = ApiClient.extractErrorMessage(response.body)
                return@withContext ChatMessagesResult(
                    emptyList(),
                    "ERROR ${response.code}: $message"
                )
            }

            val body = response.body.trim()
            if (body.isBlank()) {
                return@withContext ChatMessagesResult(emptyList(), null)
            }

            val array = JSONArray(body)
            val results = mutableListOf<ChatMessage>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                results.add(
                    ChatMessage(
                        id = item.getString("id"),
                        senderId = item.getString("senderId"),
                        content = item.getString("content"),
                        createdAt = item.getString("createdAt")
                    )
                )
            }
            ChatMessagesResult(results, null)
        } catch (ex: Exception) {
            ChatMessagesResult(emptyList(), "ERROR: ${ex.message ?: "Unknown error"}")
        }
    }
}

private suspend fun sendChannelMessage(
    context: Context,
    channelId: String,
    content: String
): ChatMessageSendResult {
    val token = AppPrefs.getToken(context)
    if (token.isNullOrBlank()) {
        return ChatMessageSendResult(
            null,
            context.getString(R.string.add_friend_requires_login)
        )
    }

    val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
    return withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("content", content)
            val response = ApiClient.executeRequest(
                method = "POST",
                url = ApiClient.buildUrl(baseUrl, "/api/channels/$channelId/messages"),
                jsonBody = body,
                token = token
            )

            if (response.code !in 200..299) {
                val message = ApiClient.extractErrorMessage(response.body)
                return@withContext ChatMessageSendResult(
                    null,
                    "ERROR ${response.code}: $message"
                )
            }

            val json = JSONObject(response.body)
            val message = ChatMessage(
                id = json.getString("id"),
                senderId = json.getString("senderId"),
                content = json.getString("content"),
                createdAt = json.getString("createdAt")
            )
            ChatMessageSendResult(message, null)
        } catch (ex: Exception) {
            ChatMessageSendResult(null, "ERROR: ${ex.message ?: "Unknown error"}")
        }
    }
}

private fun lookupUserById(baseUrl: String, token: String, query: String): LookupUser? {
    return try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = ApiClient.buildUrl(baseUrl, "/api/users/lookup") + "?query=$encodedQuery"
        val response = ApiClient.executeRequest("GET", url, token = token)
        if (response.code !in 200..299) {
            return null
        }
        val json = JSONObject(response.body)
        val id = json.getString("id")
        val username = json.getString("username")
        val displayName = json.getString("displayName")
        LookupUser(id, username, displayName)
    } catch (_: Exception) {
        null
    }
}
