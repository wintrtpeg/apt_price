package com.aptprice.tracker.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.format.AreaFormatter
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.domain.model.ComplexSummary
import com.aptprice.tracker.ui.components.AppCard
import com.aptprice.tracker.ui.components.TonalBadge
import com.aptprice.tracker.ui.theme.AppShape
import com.aptprice.tracker.ui.theme.AppSpacing

/**
 * 아파트 검색 화면.
 *
 * 두 글자 이상 치면 단지 목록이 뜨고, 하나를 고른 뒤 **조회** 를 눌러 상세로 간다.
 *
 * 검색 범위는 **이미 받아온 자료**다. 국토교통부 API 는 (시군구 × 계약월) 로만 응답하고
 * 단지명 조회를 제공하지 않아, 전국 단지 목록을 미리 갖고 있을 수 없다.
 * 결과가 없을 때 그 사실을 그대로 알린다 — 없는 단지를 지어내지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenComplex: (complexAreaKey: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // 검색하러 들어온 화면이므로 바로 입력할 수 있게 둔다.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("아파트 검색", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            OpenBar(
                selected = state.selected,
                enabled = state.canOpen,
                attributionLabel = state.attributionLabel(),
                onOpen = { state.selected?.let { onOpenComplex(it.openKey().raw) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            QueryField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clear,
                focusRequester = focusRequester,
            )

            ResultList(
                state = state,
                onSelect = viewModel::select,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun QueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Screen, vertical = 4.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        shape = AppShape.Pill,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                text = "단지명 두 글자 이상 (예: 래미안)",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "지우기")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun ResultList(
    state: SearchUiState,
    onSelect: (ComplexSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AppSpacing.Screen,
            end = AppSpacing.Screen,
            top = 12.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.results.isNotEmpty()) {
            item(key = "count") {
                Text(
                    text = "${state.results.size}개 단지",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }

        items(state.results, key = { it.complexKey }) { summary ->
            ResultRow(
                summary = summary,
                selected = state.selected?.complexKey == summary.complexKey,
                onClick = { onSelect(summary) },
            )
        }

        if (state.results.isEmpty()) {
            item(key = "hint") { SearchHint(state) }
        }
    }
}

@Composable
private fun ResultRow(
    summary: ComplexSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(
                start = AppSpacing.CardInner,
                end = 12.dp,
                top = 14.dp,
                bottom = 14.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = summary.aptName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TonalBadge(
                        text = summary.regionLabel,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "거래 ${summary.dealCount}건",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    // 어느 평형으로 열리는지 미리 알린다. 상세에서 바로 바꿀 수 있다.
                    text = "최근 ${DateFormatter.formatFeedDate(summary.latestDealDate)} · " +
                        "전용 ${AreaFormatter.formatWithBucket(summary.latestAreaM2)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = if (selected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = if (selected) "선택됨" else "선택",
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 아직 칠 게 없거나 결과가 없을 때 안내. 빈 목록을 지어낸 값으로 채우지 않는다. */
@Composable
private fun SearchHint(state: SearchUiState) {
    val text = when {
        state.message != null -> state.message
        state.tooShort -> "${SearchViewModel.MIN_LENGTH}글자 이상 입력해 주세요"
        state.query.isBlank() ->
            "단지명 일부만 쳐도 됩니다.\n메인 화면에서 받아온 자료 안에서 찾습니다."
        else -> null
    } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = DataSourceAttribution.REPORTING_DELAY_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 화면 바닥에 고정되는 조회 바. 단지를 고른 뒤 여기를 눌러야 상세로 간다. */
@Composable
private fun OpenBar(
    selected: ComplexSummary?,
    enabled: Boolean,
    attributionLabel: String,
    onOpen: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(
                    start = AppSpacing.Screen,
                    end = AppSpacing.Screen,
                    top = 10.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = selected?.let { "${it.aptName} · ${it.regionLabel}" }
                        ?: "목록에서 단지를 골라 주세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onOpen,
                    enabled = enabled,
                    shape = AppShape.Pill,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("조회", style = MaterialTheme.typography.titleMedium)
                    }
                }
                // 작업지시서 2.2 — 이 화면에서도 출처가 사라지지 않는다.
                Text(
                    text = attributionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
