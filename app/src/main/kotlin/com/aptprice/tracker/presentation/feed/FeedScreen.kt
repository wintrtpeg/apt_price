package com.aptprice.tracker.presentation.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.presentation.feed.components.DealTabRow
import com.aptprice.tracker.presentation.feed.components.EmptyStateView
import com.aptprice.tracker.presentation.feed.components.ErrorStateView
import com.aptprice.tracker.presentation.feed.components.FeedCard
import com.aptprice.tracker.presentation.feed.components.FeedSkeletonCard
import com.aptprice.tracker.presentation.feed.components.FilterBar
import com.aptprice.tracker.presentation.feed.components.NoticeBanner
import com.aptprice.tracker.presentation.feed.components.PeriodChips
import com.aptprice.tracker.presentation.feed.components.RegionFilterSheet
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 메인 피드 화면.
 *
 * 기본은 최근 2주 매매이고, 기간(최대 5년)·탭·지역·평형대·정렬을 바꿀 수 있다.
 * 화면에 뜨는 모든 값은 국토교통부 실거래가 응답에서만 온다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onDealClick: (complexAreaKey: String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRegionSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("실거래", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${state.filter.regions.summaryLabel()} · ${state.filter.period.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "인증키 설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = { AttributionBar(state.attributionLabel()) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            DealTabRow(selected = state.filter.tab, onSelect = viewModel::selectTab)

            Column(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeriodChips(
                    selected = state.filter.period,
                    onSelect = viewModel::selectPeriod,
                )

                Box {
                    FilterBar(
                        regionSummary = state.filter.regions.summaryLabel(),
                        selectedBuckets = state.filter.areaBuckets,
                        sort = state.filter.sort,
                        includeCanceled = state.filter.includeCanceled,
                        onRegionClick = { showRegionSheet = true },
                        onBucketToggle = viewModel::toggleAreaBucket,
                        onSortClick = { showSortMenu = true },
                        onCanceledToggle = viewModel::toggleIncludeCanceled,
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        FeedSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    viewModel.selectSort(sort)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
            }

            if (state.sync.inProgress) {
                SyncProgressRow(state.sync)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                FeedList(
                    state = state,
                    onDealClick = onDealClick,
                    onRetry = viewModel::refresh,
                    onDismissNotices = viewModel::dismissNotices,
                    onSettingsClick = onSettingsClick,
                )
            }
        }
    }

    if (showRegionSheet) {
        RegionFilterSheet(
            selection = state.filter.regions,
            requestCount = viewModel.currentRequestCount(),
            periodLabel = state.filter.period.label,
            onToggleGroup = viewModel::toggleRegionGroup,
            onToggleRegion = viewModel::toggleRegion,
            onSelectAll = viewModel::selectAllRegions,
            onDismiss = { showRegionSheet = false },
        )
    }

    state.heavyQueryPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissHeavyQuery,
            title = { Text("조회량이 많습니다") },
            text = { Text(prompt.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmHeavyQuery) { Text("그대로 조회") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHeavyQuery) { Text("취소") }
            },
        )
    }
}

@Composable
private fun FeedList(
    state: FeedUiState,
    onDealClick: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissNotices: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val cardSourceLabel = remember(state.lastFetchedAt) { state.compactSourceLabel() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.parseFailureNotice?.let {
            item(key = "parseNotice") { NoticeBanner(it, onDismissNotices) }
        }
        state.partialSyncNotice?.let {
            item(key = "partialNotice") { NoticeBanner(it, onDismissNotices) }
        }

        when (val content = state.content) {
            is FeedContent.Loading -> items(SKELETON_COUNT) { FeedSkeletonCard() }

            is FeedContent.Items -> items(content.items, key = { it.id }) { item ->
                FeedCard(
                    item = item,
                    sourceLabel = cardSourceLabel,
                    onClick = { onDealClick(item.complexAreaKey) },
                )
            }

            is FeedContent.Empty -> item(key = "empty") { EmptyStateView(content.message) }

            is FeedContent.Error -> item(key = "error") {
                ErrorStateView(
                    message = content.message,
                    retryable = content.retryable,
                    onRetry = onRetry,
                    // 인증키 문제라면 다시 시도해 봐야 소용없다. 설정으로 보낸다.
                    onOpenSettings = onSettingsClick.takeIf { content.needsServiceKey },
                )
            }
        }
    }
}

@Composable
private fun SyncProgressRow(sync: SyncStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = sync.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { sync.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 작업지시서 2.2 — 화면 어디서든 출처가 보이도록 하단에 고정한다. */
@Composable
private fun AttributionBar(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** 카드마다 붙이는 짧은 출처. 하단 바에는 기준일시를 포함한 전체 문구가 나온다. */
private fun FeedUiState.compactSourceLabel(): String {
    val fetchedAt = lastFetchedAt ?: return DataSourceAttribution.PROVIDER
    val local = LocalDateTime.ofInstant(fetchedAt, ZoneId.systemDefault())
    return "${DataSourceAttribution.PROVIDER} · 기준 ${DateFormatter.formatIsoDateTime(local)}"
}

private const val SKELETON_COUNT = 6
