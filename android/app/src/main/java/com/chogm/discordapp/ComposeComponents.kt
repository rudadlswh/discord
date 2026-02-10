package com.chogm.discordapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DiscordColors.ButtonPrimary,
            contentColor = DiscordColors.ButtonPrimaryText,
            disabledContainerColor = DiscordColors.ButtonPrimary.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DiscordColors.ButtonSecondaryBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = DiscordColors.ButtonSecondary,
            contentColor = DiscordColors.ButtonSecondaryText
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DiscordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    containerColor: Color = DiscordColors.InputBackground,
    textColor: Color = DiscordColors.TextPrimary,
    placeholderColor: Color = DiscordColors.TextHint
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = placeholderColor,
                fontSize = 14.sp
            )
        },
        textStyle = TextStyle(color = textColor, fontSize = 14.sp),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = DiscordColors.AccentBlue
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = DiscordColors.TextSecondaryDark,
        modifier = modifier
    )
}

@Composable
fun AvatarCircle(
    text: String,
    size: Int,
    background: Color,
    textColor: Color = DiscordColors.TextPrimaryDark
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    background: Color = DiscordColors.DarkCard,
    content: @Composable () -> Unit
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
fun PillButton(text: String, icon: Painter? = null, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DiscordColors.DarkSurfaceAlt,
        modifier = Modifier
            .height(36.dp)
            .padding(end = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = DiscordColors.AccentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DiscordColors.TextPrimaryDark
            )
        }
    }
}

@Composable
fun IconBubble(icon: Painter, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = DiscordColors.DarkSurfaceAlt,
        modifier = modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = DiscordColors.TextPrimaryDark,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun TopBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text(
            text = "←",
            fontSize = 20.sp,
            color = DiscordColors.TextPrimary
        )
    }
}
