package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.domain.region.RegionGroup
import com.aptprice.tracker.presentation.feed.RegionSelection
import com.aptprice.tracker.ui.components.ChoiceChip
import com.aptprice.tracker.ui.components.TonalBadge
import com.aptprice.tracker.ui.theme.AppShape
import com.aptprice.tracker.ui.theme.AppSpacing

/**
 * 지역 선택 시트.
 *
 * 지역을 좁히는 것은 화면을 걸러 보는 것이자 **조회량을 줄이는 수단**이기도 하다.
 * 그래서 고를 때마다 바로 조회하지 않는다. 칩을 누르는 동안에는 [draft] 만 바뀌고,
 * 아래 **확인** 을 눌러야 실제 조회 조건이 된다. 열 곳을 고르려다 아홉 번 조회하는
 * 일을 막기 위한 것이다.
 *
 * @param selection 지금 적용되어 있는 선택. 시트를 열 때의 시작값이다.
 * @param requestCountOf 그 선택으로 조회하면 몇 번을 부르게 되는지. 확인 전에 보여준다.
 * @param onApply 확인을 눌렀을 때. 이때 처음으로 조회가 일어난다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegionFilterSheet(
    selection: RegionSelection,
    requestCountOf: (RegionSelection) -> Int,
    periodLabel: String,
    onApply: (RegionSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 시트가 살아 있는 동안만 유지되는 임시 선택. 닫으면 버려진다.
    var draft by remember(selection) { mutableStateOf(selection) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            SheetHeader(
                draft = draft,
                periodLabel = periodLabel,
                onSelectAll = { draft = RegionSelection.all() },
                onClearAll = { draft = RegionSelection.none() },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = AppSpacing.Screen,
                    end = AppSpacing.Screen,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
            ) {
                items(RegionGroup.entries, key = { it.name }) { group ->
                    RegionGroupSection(
                        group = group,
                        draft = draft,
                        onToggleGroup = { draft = draft.toggleGroup(group) },
                        onToggleRegion = { code -> draft = draft.toggleRegion(code) },
                    )
                }
            }

            ApplyBar(
                draft = draft,
                requestCount = requestCountOf(draft),
                onApply = { onApply(draft) },
            )
        }
    }
}

@Composable
private fun SheetHeader(
    draft: RegionSelection,
    periodLabel: String,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.Screen, end = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("지역 선택", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$periodLabel · 고른 지역 ${draft.codes().size}곳",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClearAll, enabled = !draft.isEmpty) { Text("전체 해제") }
                TextButton(onClick = onSelectAll, enabled = !draft.isAll) { Text("전체 선택") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionGroupSection(
    group: RegionGroup,
    draft: RegionSelection,
    onToggleGroup: () -> Unit,
    onToggleRegion: (String) -> Unit,
) {
    val regions = RegionCatalog.byGroup(group)
    val chosen = regions.count { draft.contains(it.lawdCd) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.label, style = MaterialTheme.typography.titleMedium)
                if (chosen > 0) {
                    TonalBadge(
                        text = "$chosen",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            TextButton(onClick = onToggleGroup) {
                Text(
                    text = if (draft.isGroupFullySelected(group)) "전체 해제" else "전체 선택",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            regions.forEach { region ->
                ChoiceChip(
                    label = region.displayName,
                    selected = draft.contains(region.lawdCd),
                    onClick = { onToggleRegion(region.lawdCd) },
                )
            }
        }
    }
}

/** 시트 바닥에 고정되는 적용 바. 여기를 눌러야 조회가 시작된다. */
@Composable
private fun ApplyBar(
    draft: RegionSelection,
    requestCount: Int,
    onApply: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(
                    start = AppSpacing.Screen,
                    end = AppSpacing.Screen,
                    top = 12.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (draft.isEmpty) {
                        "지역을 한 곳 이상 골라 주세요"
                    } else {
                        "${draft.summaryLabel()} · 최대 ${requestCount}회 조회"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onApply,
                    enabled = !draft.isEmpty,
                    shape = AppShape.Pill,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        text = "확인",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** 시트가 화면을 거의 채우게 둔다. 지역이 36곳이라 짧으면 계속 스크롤해야 한다. */
private const val SHEET_HEIGHT_FRACTION = 0.92f
