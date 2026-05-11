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
fun AppConnectorScreen(
    apps: List<AppConnector>,
    onAppClick: (AppConnector) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.name }) { app ->
                AppIconCard(app = app, onClick = onAppClick)
            }
        }
    }
}

@Composable
private fun AppIconCard(
    app: AppConnector,
    onClick: (AppConnector) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (app.connected) Color(0xFF2A2A2A) else Color(0xFF1A1A1A),
                    RoundedCornerShape(12.dp),
                )
                .clickable { onClick(app) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.name.firstOrNull()?.uppercaseChar().toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (app.connected) Color(0xFFBEBEBE) else Color(0xFF4B4B4B),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.name,
            fontSize = 10.sp,
            color = Color(0xFF9A9A9A),
            fontWeight = FontWeight.Normal,
        )
    }
}
