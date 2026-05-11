package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolGridScreen(
    tools: List<AgentTool>,
    onToolClick: (AgentTool) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(top = 24.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tools, key = { it.name }) { tool ->
                ToolCard(tool = tool, onClick = onToolClick)
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: AgentTool,
    onClick: (AgentTool) -> Unit,
) {
    val minHeight = when (tool.name.length % 3) {
        0 -> 80.dp
        1 -> 100.dp
        else -> 90.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (tool.enabled) Color(0xFF2A2A2A) else Color(0xFF1A1A1A),
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick(tool) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Placeholder icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tool.name.firstOrNull()?.uppercaseChar().toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9A9A9A),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tool.name,
                fontSize = 11.sp,
                color = Color(0xFFE0E0E0),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
