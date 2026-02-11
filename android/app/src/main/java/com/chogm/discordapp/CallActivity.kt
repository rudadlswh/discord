package com.chogm.discordapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1201)
        }

        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val fromUserId = intent.getStringExtra(EXTRA_FROM_USER_ID)
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
        val action = intent.action

        if (!callId.isNullOrBlank() && !channelId.isNullOrBlank() && !fromUserId.isNullOrBlank()) {
            CallManager.handleIncomingCall(
                applicationContext,
                callId,
                channelId,
                fromUserId,
                callerName ?: "Caller",
                notify = false
            )
        }

        when (action) {
            ACTION_ACCEPT -> CallManager.acceptIncomingCall(applicationContext)
            ACTION_DECLINE -> {
                CallManager.rejectIncomingCall(applicationContext)
                finish()
                return
            }
        }

        setContent {
            DiscordTheme(darkTheme = true) {
                CallScreen(
                    onAccept = { CallManager.acceptIncomingCall(applicationContext) },
                    onDecline = {
                        CallManager.rejectIncomingCall(applicationContext)
                        finish()
                    },
                    onEnd = {
                        CallManager.endCall(applicationContext)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val ACTION_INCOMING = "com.chogm.discordapp.action.INCOMING_CALL"
        const val ACTION_ACCEPT = "com.chogm.discordapp.action.ACCEPT_CALL"
        const val ACTION_DECLINE = "com.chogm.discordapp.action.DECLINE_CALL"

        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_FROM_USER_ID = "extra_from_user_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
    }
}

@Composable
private fun CallScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit
) {
    val state by CallManager.state.collectAsState()
    val call by CallManager.activeCall.collectAsState()
    val isMicMuted by CallManager.isMicMuted.collectAsState()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(state) {
        if (state == CallState.IDLE) {
            activity?.finish()
        }
    }

    val title = call?.friendName ?: "Caller"
    val status = when (state) {
        CallState.INCOMING -> "통화 요청"
        CallState.OUTGOING -> "연결 중..."
        CallState.CONNECTING -> "연결 중..."
        CallState.CONNECTED -> "통화 중"
        CallState.IDLE -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, color = DiscordColors.TextPrimaryDark, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = status, color = DiscordColors.TextSecondaryDark, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))

        if (state != CallState.IDLE) {
            SecondaryButton(
                text = if (isMicMuted) "마이크 켜기" else "마이크 끄기",
                enabled = true,
                onClick = { CallManager.toggleMic() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state == CallState.INCOMING) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        } else if (state == CallState.OUTGOING || state == CallState.CONNECTING || state == CallState.CONNECTED) {
            PrimaryButton(
                text = "통화 종료",
                enabled = true,
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
