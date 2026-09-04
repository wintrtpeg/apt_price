package com.aptprice.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.ui.theme.AppShape

/**
 * 선택형 알약 칩.
 *
 * Material 의 `FilterChip` 은 선택하면 테두리가 사라지고 체크 아이콘이 끼어들어
 * 폭이 들썩인다. 가로 스크롤 줄에 여러 개를 놓으면 그 흔들림이 그대로 보인다.
 * 여기서는 **폭을 고정한 채 색만 바꾼다.**
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chipContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        label = "chipBorder",
    )

    Row(
        modifier = modifier
            .clip(AppShape.Pill)
            .background(container)
            .border(BorderStroke(1.dp, borderColor), AppShape.Pill)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = CHIP_HEIGHT)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
        trailingIcon?.let {
            CompositionLocalProvider(LocalContentColor provides content) { it() }
        }
    }
}

/** 등락률·해제 표시처럼 옅은 바탕 위에 얹는 작은 배지. */
@Composable
fun TonalBadge(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = AppShape.Badge,
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private val CHIP_HEIGHT = 36.dp
