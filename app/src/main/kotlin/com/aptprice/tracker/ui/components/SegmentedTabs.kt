package com.aptprice.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.ui.theme.AppShape

/**
 * 세그먼트 탭 (iOS 세그먼티드 컨트롤 계열).
 *
 * Material 기본 탭은 밑줄 하나로만 선택을 표시해서 밋밋하고, 3개짜리 짧은 목록에는
 * 과하게 넓다. 알약형 컨테이너 안에서 선택 표시가 미끄러지듯 움직이게 해
 * 지금 어느 탭인지 한눈에 들어오게 한다.
 *
 * 항목 폭은 모두 같으므로 표시 위치는 `한 칸 너비 × 선택 index` 로 계산한다.
 */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SEGMENT_HEIGHT)
            .clip(AppShape.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(TRACK_PADDING),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val itemWidth = maxWidth / options.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "segmentIndicator",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(AppShape.Pill)
                    .background(MaterialTheme.colorScheme.surface),
            )

            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "segmentLabel",
                    )
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(AppShape.Pill)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(option) },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = labelOf(option),
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

private val SEGMENT_HEIGHT = 44.dp
private val TRACK_PADDING = 4.dp
