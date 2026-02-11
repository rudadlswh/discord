package com.chogm.discordapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun WelcomeScreen(onSignup: () -> Unit, onLogin: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(DiscordColors.WelcomeStart, DiscordColors.WelcomeEnd)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(DiscordColors.WelcomeOrbSoft, CircleShape)
                .align(Alignment.TopStart)
                .padding(0.dp)
                .offset(x = 24.dp, y = 80.dp)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(DiscordColors.WelcomeOrbBright, CircleShape)
                .align(Alignment.TopEnd)
                .offset(x = (-24).dp, y = 220.dp)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(DiscordColors.WelcomeOrbSoft, CircleShape)
                .align(Alignment.BottomStart)
                .offset(x = 40.dp, y = (-80).dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(id = R.string.app_name),
                color = DiscordColors.WelcomeTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(id = R.string.welcome_title),
                color = DiscordColors.WelcomeTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )

            Text(
                text = stringResource(id = R.string.welcome_subtitle),
                color = DiscordColors.WelcomeTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            SecondaryButton(text = stringResource(id = R.string.welcome_signup), onClick = onSignup)

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryButton(text = stringResource(id = R.string.auth_login_button), onClick = onLogin)
        }
    }
}

@Composable
fun LoginScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.LightBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TopBackButton(onBack)

        Text(
            text = stringResource(id = R.string.title_login),
            color = DiscordColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp)
        )

        Text(
            text = stringResource(id = R.string.login_subtitle),
            color = DiscordColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(id = R.string.login_email_or_phone),
            color = DiscordColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 24.dp)
        )

        DiscordTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(id = R.string.login_email_or_phone),
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(id = R.string.auth_password_hint),
            color = DiscordColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        DiscordTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(id = R.string.auth_password_hint),
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(id = R.string.login_password_toggle),
                        tint = DiscordColors.TextSecondary
                    )
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(id = R.string.login_forgot_password),
            color = DiscordColors.Blurple,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        PrimaryButton(
            text = if (isLoading) stringResource(id = R.string.auth_login_in_progress)
            else stringResource(id = R.string.auth_login_button),
            enabled = !isLoading,
            onClick = {
                val trimmedEmail = email.trim()
                if (trimmedEmail.isBlank() || password.isBlank()) {
                    errorMessage = if (trimmedEmail.isBlank()) {
                        context.getString(R.string.register_required_email)
                    } else {
                        context.getString(R.string.register_required_password)
                    }
                    return@PrimaryButton
                }

                val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
                isLoading = true
                errorMessage = null

                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val body = JSONObject()
                                .put("email", trimmedEmail)
                                .put("password", password)
                            val response = ApiClient.executeRequest(
                                method = "POST",
                                url = ApiClient.buildUrl(baseUrl, "/api/auth/login"),
                                jsonBody = body
                            )

                            if (response.code in 200..299) {
                                val json = JSONObject(response.body)
                                val token = json.getString("token")
                                val user = json.getJSONObject("user")
                                val userId = user.getString("id")
                                val username = user.getString("username")
                                val displayName = user.getString("displayName")

                                AppPrefs.saveAuth(context, token, userId, username, displayName)
                                AppPrefs.setBaseUrl(context, baseUrl)
                                PushTokenManager.refreshToken(context)
                                "OK"
                            } else {
                                "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
                            }
                        } catch (ex: Exception) {
                            "ERROR: ${ex.message}"
                        }
                    }

                    isLoading = false
                    if (result.startsWith("OK")) {
                        onLoginSuccess()
                    } else {
                        errorMessage = result
                    }
                }
            }
        )

        Text(
            text = stringResource(id = R.string.login_passkey),
            color = DiscordColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RegisterEmailScreen(onBack: () -> Unit, onNext: (String) -> Unit) {
    val context = LocalContext.current
    var useEmail by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.LightBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TopBackButton(onBack)

        Text(
            text = stringResource(id = R.string.register_email_title),
            color = DiscordColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .background(DiscordColors.InputBackground, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            SegmentedOption(
                text = stringResource(id = R.string.register_toggle_phone),
                selected = !useEmail,
                onClick = { useEmail = false },
                modifier = Modifier.weight(1f)
            )
            SegmentedOption(
                text = stringResource(id = R.string.register_toggle_email),
                selected = useEmail,
                onClick = { useEmail = true },
                modifier = Modifier.weight(1f)
            )
        }

        if (useEmail) {
            Text(
                text = stringResource(id = R.string.auth_email_hint),
                color = DiscordColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 20.dp)
            )

            DiscordTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = stringResource(id = R.string.auth_email_hint),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = stringResource(id = R.string.auth_phone_hint),
                color = DiscordColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 20.dp)
            )

            DiscordTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = stringResource(id = R.string.auth_phone_hint),
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = stringResource(id = R.string.register_phone_not_supported),
                color = DiscordColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        PrimaryButton(
            text = stringResource(id = R.string.next_button),
            onClick = {
                val identifier = if (useEmail) email.trim() else phone.trim()
                if (identifier.isBlank()) {
                    errorMessage = if (useEmail) {
                        context.getString(R.string.register_required_email)
                    } else {
                        context.getString(R.string.register_required_phone)
                    }
                    return@PrimaryButton
                }
                errorMessage = null
                onNext(identifier)
            },
            enabled = true
        )
    }
}

@Composable
fun RegisterCredentialsScreen(
    onBack: () -> Unit,
    onNext: (String, String) -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.LightBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TopBackButton(onBack)

        Text(
            text = stringResource(id = R.string.register_credentials_title),
            color = DiscordColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Text(
            text = stringResource(id = R.string.auth_username_hint),
            color = DiscordColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 20.dp)
        )

        DiscordTextField(
            value = username,
            onValueChange = { username = it },
            placeholder = stringResource(id = R.string.auth_username_hint),
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(id = R.string.auth_password_hint),
            color = DiscordColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        DiscordTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(id = R.string.auth_password_hint),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.padding(top = 8.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        PrimaryButton(
            text = stringResource(id = R.string.next_button),
            onClick = {
                if (username.trim().isBlank()) {
                    errorMessage = context.getString(R.string.register_required_username)
                    return@PrimaryButton
                }
                if (password.isBlank()) {
                    errorMessage = context.getString(R.string.register_required_password)
                    return@PrimaryButton
                }
                errorMessage = null
                onNext(username.trim(), password)
            }
        )
    }
}

@Composable
fun RegisterDisplayNameScreen(
    email: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiscordColors.LightBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBackButton(onBack)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(id = R.string.register_skip),
                color = DiscordColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(end = 8.dp)
            )
        }

        Text(
            text = stringResource(id = R.string.register_display_title),
            color = DiscordColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Text(
            text = stringResource(id = R.string.auth_display_name_hint),
            color = DiscordColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 20.dp)
        )

        DiscordTextField(
            value = displayName,
            onValueChange = { displayName = it },
            placeholder = stringResource(id = R.string.register_display_hint),
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(id = R.string.register_display_helper),
            color = DiscordColors.TextHint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        PrimaryButton(
            text = if (isLoading) stringResource(id = R.string.auth_register_in_progress)
            else stringResource(id = R.string.next_button),
            enabled = !isLoading,
            onClick = {
                val finalDisplayName = displayName.trim().ifBlank { username }
                if (finalDisplayName.isBlank()) {
                    errorMessage = context.getString(R.string.register_required_display_name)
                    return@PrimaryButton
                }

                val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
                isLoading = true
                errorMessage = null

                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val body = JSONObject()
                                .put("email", email)
                                .put("username", username)
                                .put("displayName", finalDisplayName)
                                .put("password", password)
                            val response = ApiClient.executeRequest(
                                method = "POST",
                                url = ApiClient.buildUrl(baseUrl, "/api/auth/register"),
                                jsonBody = body
                            )

                            if (response.code in 200..299) {
                                val json = JSONObject(response.body)
                                val token = json.getString("token")
                                val user = json.getJSONObject("user")
                                val userId = user.getString("id")
                                val savedUsername = user.getString("username")
                                val savedDisplayName = user.getString("displayName")

                                AppPrefs.saveAuth(context, token, userId, savedUsername, savedDisplayName)
                                AppPrefs.setBaseUrl(context, baseUrl)
                                PushTokenManager.refreshToken(context)
                                "OK"
                            } else {
                                "ERROR ${response.code}: ${ApiClient.extractErrorMessage(response.body)}"
                            }
                        } catch (ex: Exception) {
                            "ERROR: ${ex.message}"
                        }
                    }

                    isLoading = false
                    if (result.startsWith("OK")) {
                        onSuccess()
                    } else {
                        errorMessage = result
                    }
                }
            }
        )
    }
}

@Composable
private fun SegmentedOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val background = if (selected) DiscordColors.ButtonSecondary else Color.Transparent
    val textColor = if (selected) DiscordColors.TextPrimary else DiscordColors.TextSecondary
    val weight = if (selected) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = modifier
            .height(36.dp)
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 14.sp, fontWeight = weight)
    }
}
