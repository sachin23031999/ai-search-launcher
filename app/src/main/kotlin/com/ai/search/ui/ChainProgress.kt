package com.ai.search.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.search.model.ChainStep
import com.ai.search.model.StepStatus

/** Renders the live multi-step chain so the user can watch the agent's progress. */
@Composable
fun ChainProgress(steps: List<ChainStep>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (step in steps) {
            ChainStepRow(step)
        }
    }
}

@Composable
private fun ChainStepRow(step: ChainStep) {
    val dotColor by animateColorAsState(
        when (step.status) {
            StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
            StepStatus.DONE -> Color(0xFF2E7D32)
            StepStatus.ERROR -> MaterialTheme.colorScheme.error
        }
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (step.status == StepStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Box(Modifier.size(14.dp).clip(CircleShape).background(dotColor))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (step.detail.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
