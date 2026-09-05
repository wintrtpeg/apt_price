package com.aptprice.tracker.presentation.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.presentation.feed.components.DealTabRow
import com.aptprice.tracker.presentation.feed.components.EmptyStateView
import com.aptprice.tracker.presentation.feed.components.ErrorStateView
import com.aptprice.tracker.presentation.feed.components.FeedCard
import com.aptprice.tracker.presentation.feed.components.FeedSkeletonCard
import com.aptprice.tracker.presentation.feed.components.FeedSummaryCard
import com.aptprice.tracker.presentation.feed.components.FilterBar
import com.aptprice.tracker.presentation.feed.components.NoticeBanner
import com.aptprice.tracker.presentation.feed.components.PeriodChips
import com.aptprice.tracker.presentation.feed.components.RegionFilterSheet
import com.aptprice.tracker.ui.theme.AppShape
import com.aptprice.tracker.ui.theme.AppSpacing
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
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRegionSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("실거래", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "${state.filter.regions.summaryLabel()} · ${state.filter.period.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, contentDescription = "아파트 검색")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Tune, contentDescription = "인증키 설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = { AttributionBar(state.attributionLabel()) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            FeedControls(
                state = state,
                showSortMenu = showSortMenu,
                onOpenSortMenu = { showSortMenu = true },
                onDismissSortMenu = { showSortMenu = false },
                onSelectSort = viewModel::selectSort,
                onSelectTab = viewModel::selectTab,
                onSelectPeriod = viewModel::selectPeriod,
                onRegionClick = { showRegionSheet = true },
                onBucketToggle = viewModel::toggleAreaBucket,
                onCanceledToggle = viewModel::toggleIncludeCanceled,
            )

            AnimatedVisibility(
                visible = state.sync.inProgress,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SyncProgressRow(state.sync)
            }

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
            requestCountOf = viewModel::requestCountFor,
            periodLabel = state.filter.period.label,
            // 확인을 눌렀을 때만 조회 조건이 바뀐다. 칩을 누를 때마다 부르지 않는다.
            onApply = { selection ->
                viewModel.applyRegions(selection)
                showRegionSheet = false
            },
            onDismiss = { showRegionSheet = false },
        )
    }

    state.heavyQueryPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissHeavyQuery,
            shape = AppShape.Card,
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

/** 탭 · 기간 · 필터를 한 덩어리로 묶어 목록 위에 고정한다. */
@Composable
private fun FeedControls(
    state: FeedUiState,
    showSortMenu: Boolean,
    onOpenSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSelectSort: (FeedSort) -> Unit,
    onSelectTab: (DealTab) -> Unit,
    onSelectPeriod: (TradePeriod) -> Unit,
    onRegionClick: () -> Unit,
    onBucketToggle: (AreaBucket) -> Unit,
    onCanceledToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DealTabRow(
            selected = state.filter.tab,
            onSelect = onSelectTab,
            modifier = Modifier.padding(horizontal = AppSpacing.Screen),
        )

        PeriodChips(selected = state.filter.period, onSelect = onSelectPeriod)

        Box {
            FilterBar(
                regionSummary = state.filter.regions.summaryLabel(),
                selectedBuckets = state.filter.areaBuckets,
                sort = state.filter.sort,
                includeCanceled = state.filter.includeCanceled,
                hasRegion = !state.filter.regions.isEmpty,
                onRegionClick = onRegionClick,
                onBucketToggle = onBucketToggle,
                onSortClick = onOpenSortMenu,
                onCanceledToggle = onCanceledToggle,
            )
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = onDismissSortMenu,
            ) {
                FeedSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            onSelectSort(sort)
                            onDismissSortMenu()
                        },
                    )
                }
            }
        }
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
        contentPadding = PaddingValues(
            start = AppSpacing.Screen,
            end = AppSpacing.Screen,
            top = 4.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.parseFailureNotice?.let {
            item(key = "parseNotice") { NoticeBanner(it, onDismissNotices) }
        }
        state.partialSyncNotice?.let {
            item(key = "partialNotice") { NoticeBanner(it, onDismissNotices) }
        }

        // 목록 맨 위 요약. 거래가 있을 때만 나온다.
        state.summary?.let { summary ->
            if (state.content is FeedContent.Items) {
                item(key = "summary") {
                    FeedSummaryCard(summary = summary, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }

        when (val content = state.content) {
            is FeedContent.Loading -> items(SKELETON_COUNT) { FeedSkeletonCard() }

            is FeedContent.Items -> items(content.items, key = { it.id }) { item ->
                FeedCard(
                    item = item,
                    sourceLabel = cardSourceLabel,
                    onClick = { onDealClick(item.complexAreaKey) },
                    sparkline = state.sparklines[item.complexAreaKey],
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
            .padding(horizontal = AppSpacing.Screen, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = sync.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { sync.fraction },
            modifier = Modifier.fillMaxWidth(),
            strokeCap = StrokeCap.Round,
        )
    }
}

/** 작업지시서 2.2 — 화면 어디서든 출처가 보이도록 하단에 고정한다. */
@Composable
private fun AttributionBar(label: String) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Screen, vertical = 8.dp),
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
