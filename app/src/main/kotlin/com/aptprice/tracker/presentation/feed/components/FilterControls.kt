package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.presentation.feed.FeedSort

/** 매매 / 전세 / 월세 3-Way 탭. */
@Composable
fun DealTabRow(
    selected: DealTab,
    onSelect: (DealTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = DealTab.entries.indexOf(selected),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DealTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }
    }
}

/**
 * 조회 기간 선택. 기본 2주에서 최대 5년까지.
 * 긴 기간은 조회량이 크므로 [requestCountOf] 로 예상 횟수를 함께 보여준다.
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
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(period.shortLabel, style = MaterialTheme.typography.labelLarge) },
                shape = FilterChipDefaults.shape,
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = true,
            onClick = onRegionClick,
            label = { Text(regionSummary, style = MaterialTheme.typography.labelLarge) },
            trailingIcon = {
                Icon(Icons.Filled.ExpandMore, contentDescription = "지역 선택")
            },
        )

        AreaBucket.entries.forEach { bucket ->
            FilterChip(
                selected = bucket in selectedBuckets,
                onClick = { onBucketToggle(bucket) },
                label = { Text(bucket.label, style = MaterialTheme.typography.labelLarge) },
            )
        }

        FilterChip(
            selected = false,
            onClick = onSortClick,
            label = { Text(sort.label, style = MaterialTheme.typography.labelLarge) },
            trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = "정렬 선택") },
        )

        FilterChip(
            selected = !includeCanceled,
            onClick = onCanceledToggle,
            label = { Text("해제 건 숨기기", style = MaterialTheme.typography.labelLarge) },
        )
    }
}
