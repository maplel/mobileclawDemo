package com.mobilebot.chat.ui.runtime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentBottomBar(
    isListening: Boolean = false,
    isLiveMode: Boolean = false,
    inputMode: Boolean = false,
    onAddClick: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onTalkClick: () -> Unit = {},
    onLiveClick: () -> Unit = {},
    onShowToolGrid: () -> Unit = {},
    onShowAppConnectors: () -> Unit = {},
) {
    var inputText by remember { mutableStateOf("") }

    AnimatedContent(
        targetState = inputMode,
        transitionSpec = {
            (fadeIn() + fadeIn()).togetherWith(fadeOut() + fadeOut())
        },
        label = "inputMode",
    ) { isInput ->
        if (isInput) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF111111),
                border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text("Type a message...", fontSize = 13.sp, color = Color(0xFF6F6F6F))
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color(0xFF2FE8C8),
                        ),
                    )
                    if (inputText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2FE8C8).copy(alpha = 0.3f))
                                .clickable {
                                    val text = inputText
                                    inputText = ""
                                    onSendMessage(text)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF2FE8C8),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF111111),
                border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2A2A))
                            .clickable { onAddClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    BottomPill(
                        label = "Talk",
                        selected = isListening,
                        onClick = onTalkClick,
                    )
                    BottomPill(
                        label = "Live",
                        selected = isLiveMode,
                        onClick = onLiveClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) Color(0xFF3A3A3A).copy(alpha = 0.4f) else Color(0xFF3A3A3A).copy(alpha = 0.15f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color(0xFF2FE8C8) else Color(0xFF9A9A9A),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
