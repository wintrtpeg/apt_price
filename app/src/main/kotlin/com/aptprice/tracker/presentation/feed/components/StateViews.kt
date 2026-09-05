package com.aptprice.tracker.presentation.feed.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.ui.components.AppCard
import com.aptprice.tracker.ui.theme.AppShape
import com.aptprice.tracker.ui.theme.AppSpacing

/** 첫 로딩 동안 보여주는 뼈대. 실제 값처럼 보이는 숫자는 절대 넣지 않는다. */
@Composable
fun FeedSkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
    val shimmer = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(progress * 900f - 300f, 0f),
        end = Offset(progress * 900f, 0f),
    )

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBar(shimmer, widthFraction = 0.30f, height = 14.dp)
            SkeletonBar(shimmer, widthFraction = 0.62f, height = 18.dp)
            SkeletonBar(shimmer, widthFraction = 0.44f, height = 26.dp)
            SkeletonBar(shimmer, widthFraction = 0.78f, height = 12.dp)
        }
    }
}

@Composable
private fun SkeletonBar(brush: Brush, widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(AppShape.Badge)
            .background(brush),
    )
}

/**
 * 조회 결과가 없을 때.
 * 빈 목록을 지어낸 값으로 채우지 않고, 없다는 사실과 이유를 그대로 보여준다.
 */
@Composable
fun EmptyStateView(message: String, modifier: Modifier = Modifier) {
    StateBlock(
        modifier = modifier,
        icon = Icons.Outlined.SearchOff,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        iconContainer = MaterialTheme.colorScheme.surfaceContainer,
        title = message,
        titleColor = MaterialTheme.colorScheme.onSurface,
        detail = DataSourceAttribution.REPORTING_DELAY_NOTICE,
    )
}

/** 조회 자체가 실패했을 때. 원인을 감추지 않는다. */
@Composable
fun ErrorStateView(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** 인증키 문제일 때만 설정으로 가는 버튼을 띄운다. */
    onOpenSettings: (() -> Unit)? = null,
) {
    StateBlock(
        modifier = modifier,
        icon = Icons.Outlined.ErrorOutline,
        iconTint = MaterialTheme.colorScheme.error,
        iconContainer = MaterialTheme.colorScheme.errorContainer,
        title = message,
        titleColor = MaterialTheme.colorScheme.onSurface,
        detail = null,
    ) {
        when {
            // 인증키가 문제면 다시 시도해 봐야 같은 결과다.
            onOpenSettings != null -> Button(
                onClick = onOpenSettings,
                shape = AppShape.Pill,
            ) { Text("인증키 설정하기") }

            retryable -> Button(onClick = onRetry, shape = AppShape.Pill) { Text("다시 시도") }
        }
    }
}

/** 빈 결과·오류가 같은 모양으로 보이도록 묶은 틀. */
@Composable
private fun StateBlock(
    icon: ImageVector,
    iconTint: Color,
    iconContainer: Color,
    title: String,
    titleColor: Color,
    detail: String?,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/** 파서 실패·부분 실패처럼 숨기면 안 되는 알림. */
@Composable
fun NoticeBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape.Inner,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    }
}
