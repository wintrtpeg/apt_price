package com.aptprice.tracker.presentation.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.presentation.detail.components.ChartLegend
import com.aptprice.tracker.presentation.detail.components.PriceChart
import com.aptprice.tracker.ui.components.AppCard
import com.aptprice.tracker.ui.components.ChoiceChip
import com.aptprice.tracker.ui.theme.AppSpacing

/**
 * 단지 상세 — 평형 선택 칩 + 실거래가 시계열 차트 + 거래 이력.
 *
 * 차트와 이력은 같은 기간(3개월~5년)을 공유한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.aptName.ifEmpty { "단지" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOf(state.regionLabel, state.areaLabel)
                                .filter { it.isNotEmpty() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = { AttributionBar(state.attributionLabel()) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.areaChips.isNotEmpty()) {
                item(key = "areaChips") {
                    AreaChipRow(chips = state.areaChips, onSelect = viewModel::selectArea)
                }
            }

            item(key = "periodChips") {
                PeriodRow(selected = state.period, onSelect = viewModel::selectPeriod)
            }

            item(key = "chart") {
                val chart = state.chart
                when {
                    chart == null || chart.isEmpty -> ChartEmptyView(
                        message = state.emptyMessage ?: DataSourceAttribution.EMPTY_RESULT,
                    )
                    else -> AppCard(
                        modifier = Modifier.padding(horizontal = AppSpacing.Screen),
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.CardInner),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PriceChart(data = chart)
                            ChartLegend(data = chart)
                            Text(
                                text = "점은 실제 신고된 거래입니다. 신고가 오래 없던 구간은 " +
                                    "선을 잇지 않습니다.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item(key = "historyHeader") { HistoryHeader(state.history.size) }
                items(state.history, key = { it.id }) { row -> HistoryRowView(row) }
            }
        }
    }
}

@Composable
private fun AreaChipRow(chips: List<AreaChip>, onSelect: (AreaChip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "평형 선택",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = AppSpacing.Screen),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { chip ->
                ChoiceChip(
                    label = chip.label,
                    selected = chip.selected,
                    onClick = { onSelect(chip) },
                )
            }
        }
    }
}

@Composable
private fun PeriodRow(selected: TradePeriod, onSelect: (TradePeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TradePeriod.chartOptions.forEach { period ->
            ChoiceChip(
                label = period.shortLabel,
                selected = period == selected,
                onClick = { onSelect(period) },
            )
        }
    }
}

@Composable
private fun ChartEmptyView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = DataSourceAttribution.REPORTING_DELAY_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryHeader(count: Int) {
    Text(
        text = "거래 이력 ${count}건",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(
            start = AppSpacing.Screen,
            end = AppSpacing.Screen,
            top = 12.dp,
        ),
    )
}

@Composable
private fun HistoryRowView(row: HistoryRow) {
    val decoration = if (row.canceled) TextDecoration.LineThrough else TextDecoration.None
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Screen, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = row.dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = decoration,
            )
            Text(
                text = listOfNotNull(
                    row.typeLabel,
                    row.floorLabel,
                    if (row.canceled) DataSourceAttribution.CANCELED_BADGE else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (row.canceled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = row.priceLabel + if (row.isPeak) "  ▲최고" else "",
            style = MaterialTheme.typography.titleSmall,
            color = if (row.isPeak) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = decoration,
        )
    }
}

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
