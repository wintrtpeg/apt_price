package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.domain.region.RegionGroup
import com.aptprice.tracker.presentation.feed.RegionSelection

/**
 * 지역 선택 시트.
 *
 * 지역을 좁히는 것은 화면을 걸러 보는 것이자 **조회량을 줄이는 수단**이기도 하다.
 * 긴 기간을 고른 상태에서 특히 중요하므로, 예상 조회 횟수를 함께 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegionFilterSheet(
    selection: RegionSelection,
    requestCount: Int,
    periodLabel: String,
    onToggleGroup: (RegionGroup) -> Unit,
    onToggleRegion: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("지역 선택", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "$periodLabel · 선택한 지역 ${selection.codes().size}곳 " +
                            "· 최대 ${requestCount}회 조회",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSelectAll) { Text("전체 선택") }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(RegionGroup.entries, key = { it.name }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.label, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { onToggleGroup(group) }) {
                                Text(
                                    if (selection.isGroupFullySelected(group)) "전체 해제" else "전체 선택",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RegionCatalog.byGroup(group).forEach { region ->
                                FilterChip(
                                    selected = selection.contains(region.lawdCd),
                                    onClick = { onToggleRegion(region.lawdCd) },
                                    label = {
                                        Text(
                                            region.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
