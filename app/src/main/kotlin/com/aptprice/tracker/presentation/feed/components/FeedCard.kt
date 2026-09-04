package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.presentation.feed.ChangeDirection
import com.aptprice.tracker.presentation.feed.FeedItemUi
import com.aptprice.tracker.ui.theme.LocalTrendColors
import com.aptprice.tracker.ui.theme.PriceTextStyle

/**
 * 실거래 한 건을 보여주는 카드.
 *
 * 금액을 시선의 중심에 두고 나머지는 한 단계 낮춘다.
 * 원자료에 없는 값(층·준공연도·등락률)은 자리를 비운다. 채워 넣지 않는다.
 */
@Composable
fun FeedCard(
    item: FeedItemUi,
    sourceLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trend = LocalTrendColors.current
    val strikeThrough = if (item.canceled) TextDecoration.LineThrough else TextDecoration.None

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.regionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.canceled) CanceledBadge()
                    Text(
                        text = item.dateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = item.aptName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = strikeThrough,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.priceLabel,
                        style = PriceTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textDecoration = strikeThrough,
                    )
                    item.priceSubLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 비교할 직전 거래가 없으면 등락률 자리를 비운다.
                item.changeLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = when (item.changeDirection) {
                            ChangeDirection.UP -> trend.up
                            ChangeDirection.DOWN -> trend.down
                            else -> trend.flat
                        },
                    )
                }
            }

            Text(
                text = listOfNotNull(
                    item.areaLabel,
                    item.floorLabel,
                    item.buildYearLabel,
                    item.relativeDateLabel,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 작업지시서 2.2 — 모든 카드에 출처를 표기한다.
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CanceledBadge() {
    Text(
        text = com.aptprice.tracker.core.attribution.DataSourceAttribution.CANCELED_BADGE,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
