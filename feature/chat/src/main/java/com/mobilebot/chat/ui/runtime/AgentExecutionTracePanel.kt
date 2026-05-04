package com.mobilebot.chat.ui.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
fun AgentExecutionTracePanel(
    taskTitle: String,
    subAgentName: String,
    steps: List<AgentTraceStep>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF4E4E4E), RoundedCornerShape(28.dp))
            .padding(16.dp),
    ) {
        // Title and subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = taskTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = subAgentName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFBEBEBE),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = Color(0xFF6B6B6B),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        // Timeline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((index, step) in steps.withIndex()) {
                TraceStepItem(step, index)
            }
        }
    }
}

@Composable
private fun TraceStepItem(step: AgentTraceStep, index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Timeline dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (step.status) {
                            TraceStatus.Running -> Color(0xFF2FE8C8)
                            TraceStatus.Completed -> Color(0xFF9A9A9A)
                            TraceStatus.Pending -> Color(0xFF3A3A3A)
                            TraceStatus.Blocked -> Color(0xFFFFC107)
                            TraceStatus.Failed -> Color(0xFFFF9800)
                        },
                    ),
            )
            // Timeline line (not for last item)
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color(0xFF6B6B6B).copy(alpha = 0.4f)),
            )
        }

        // Step content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.time,
                fontSize = 9.sp,
                color = Color(0xFF6F6F6F),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = step.title,
                fontSize = 12.sp,
                color = Color(0xFFE0E0E0),
                fontWeight = FontWeight.Normal,
            )
            if (step.description.isNotBlank()) {
                Text(
                    text = step.description,
                    fontSize = 10.sp,
                    color = Color(0xFF9A9A9A),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
