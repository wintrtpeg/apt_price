package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.presentation.feed.ChangeDirection
import com.aptprice.tracker.presentation.feed.FeedItemUi
import com.aptprice.tracker.ui.components.AppCard
import com.aptprice.tracker.ui.components.TonalBadge
import com.aptprice.tracker.ui.theme.AppSpacing
import com.aptprice.tracker.ui.theme.LocalTrendColors
import com.aptprice.tracker.ui.theme.PriceTextStyle

/**
 * 실거래 한 건을 보여주는 카드.
 *
 * 위에서 아래로 **어디(지역) → 무엇(단지·평형) → 얼마(금액)** 순으로 읽히게 세운다.
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

    AppCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(
                start = AppSpacing.CardInner,
                end = AppSpacing.CardInner,
                top = AppSpacing.CardInner,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Tight),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TonalBadge(
                    text = item.regionLabel,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (item.canceled) {
                    TonalBadge(
                        text = DataSourceAttribution.CANCELED_BADGE,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    text = item.dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = item.aptName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = strikeThrough,
            )

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

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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
                    TonalBadge(
                        text = label,
                        container = when (item.changeDirection) {
                            ChangeDirection.UP -> trend.upContainer
                            ChangeDirection.DOWN -> trend.downContainer
                            else -> trend.flatContainer
                        },
                        content = when (item.changeDirection) {
                            ChangeDirection.UP -> trend.up
                            ChangeDirection.DOWN -> trend.down
                            else -> trend.flat
                        },
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }

        // 작업지시서 2.2 — 모든 카드에 출처를 표기한다.
        Text(
            text = sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.CardInner,
                    end = AppSpacing.CardInner,
                    bottom = 12.dp,
                ),
        )
    }
}
