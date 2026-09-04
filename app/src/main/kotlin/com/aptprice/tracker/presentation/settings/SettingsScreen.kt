package com.aptprice.tracker.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.ui.theme.AppShape
import com.aptprice.tracker.ui.theme.AppSpacing

/**
 * 공공데이터포털 인증키 설정.
 *
 * 키를 앱에서 입력받으면 APK 안에 키를 넣지 않아도 되므로, 공개 저장소에서 APK 를
 * 내려받아 쓰더라도 키가 함께 유출되지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
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
                title = { Text("인증키 설정", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Screen, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusRow(isConfigured = state.isConfigured)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("발급 방법", style = MaterialTheme.typography.titleSmall)
            Text(
                text = """
                    1. data.go.kr 에 로그인합니다.
                    2. 아래 두 API 를 모두 활용신청합니다.
                       · 국토교통부_아파트 매매 실거래가 자료
                       · 국토교통부_아파트 전월세 자료
                    3. 마이페이지 → 개발계정 상세보기 →
                       "일반 인증키(Decoding)" 를 복사해 아래에 붙여넣습니다.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Encoding 키를 넣으면 이중 인코딩이 되어 인증에 실패합니다. " +
                    "반드시 Decoding 키를 넣어 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.Inner,
                label = { Text("일반 인증키 (Decoding)") },
                placeholder = { Text("키를 붙여넣으세요") },
                singleLine = false,
                minLines = 2,
                isError = state.hint.isBlocking,
                supportingText = {
                    state.hint.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.input.isNotBlank(),
                    shape = AppShape.Pill,
                ) {
                    Text("저장")
                }
                if (state.isConfigured) {
                    TextButton(onClick = viewModel::clear) { Text("저장된 키 지우기") }
                }
            }

            state.savedMessage?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = AppShape.Inner,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissMessage) { Text("확인") }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "인증키는 이 기기에만 저장되며 외부로 전송되지 않습니다. " +
                    "실거래 조회 외의 용도로 쓰이지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = DataSourceAttribution.API_NAMES.joinToString("\n") { "· $it" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusRow(isConfigured: Boolean) {
    Surface(
        color = if (isConfigured) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        shape = AppShape.Card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isConfigured) "인증키가 설정되어 있습니다" else "인증키가 설정되지 않았습니다",
                style = MaterialTheme.typography.titleSmall,
                color = if (isConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = if (isConfigured) {
                    "실거래가를 조회할 수 있습니다."
                } else {
                    "키가 없으면 실거래가를 조회할 수 없습니다. 임의의 값으로 채우지 않습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
