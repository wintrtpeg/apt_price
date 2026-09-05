package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.ui.theme.AppSpacing
import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.presentation.feed.FeedSort
import com.aptprice.tracker.ui.components.ChoiceChip
import com.aptprice.tracker.ui.components.SegmentedTabs

/** 매매 / 전세 / 월세 3-Way 탭. */
@Composable
fun DealTabRow(
    selected: DealTab,
    onSelect: (DealTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedTabs(
        options = DealTab.entries,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it.label },
        modifier = modifier,
    )
}

/**
 * 조회 기간 선택. 기본 2주에서 최대 5년까지.
 * 긴 기간은 조회량이 크므로 지역 시트에서 예상 횟수를 함께 보여준다.
 */
@Composable
fun PeriodChips(
    selected: TradePeriod,
    options: List<TradePeriod> = TradePeriod.feedOptions,
    onSelect: (TradePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { period ->
            ChoiceChip(
                label = period.shortLabel,
                selected = period == selected,
                onClick = { onSelect(period) },
            )
        }
    }
}

/** 지역 요약 · 평형대 · 정렬 · 해제 건 표시를 한 줄에 모은다. */
@Composable
fun FilterBar(
    regionSummary: String,
    selectedBuckets: Set<AreaBucket>,
    sort: FeedSort,
    includeCanceled: Boolean,
    onRegionClick: () -> Unit,
    onBucketToggle: (AreaBucket) -> Unit,
    onSortClick: () -> Unit,
    onCanceledToggle: () -> Unit,
    modifier: Modifier = Modifier,
    hasRegion: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 지역을 아직 고르지 않았으면 이 칩이 다음에 눌러야 할 곳이다. 강조해 둔다.
        ChoiceChip(
            label = regionSummary,
            selected = hasRegion,
            onClick = onRegionClick,
            trailingIcon = {
                Icon(
                    imageVector = if (hasRegion) Icons.Filled.ExpandMore else Icons.Filled.Place,
                    contentDescription = "지역 선택",
                    modifier = Modifier.padding(top = 1.dp),
                )
            },
        )

        AreaBucket.entries.forEach { bucket ->
            ChoiceChip(
                label = bucket.label,
                selected = bucket in selectedBuckets,
                onClick = { onBucketToggle(bucket) },
            )
        }

        ChoiceChip(
            label = sort.label,
            selected = false,
            onClick = onSortClick,
            trailingIcon = {
                Icon(Icons.Filled.ExpandMore, contentDescription = "정렬 선택")
            },
        )

        ChoiceChip(
            label = "해제 건 숨기기",
            selected = !includeCanceled,
            onClick = onCanceledToggle,
        )
    }
}
