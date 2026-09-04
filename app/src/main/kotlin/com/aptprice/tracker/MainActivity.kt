package com.aptprice.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.ui.theme.AptPriceTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AptPriceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Step1Placeholder(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

/**
 * Step 1 확인용 임시 화면.
 *
 * 실거래 피드(Step 3)가 붙기 전까지 자리를 지키며, Step 1 산출물인
 * 폰트·지역 카탈로그·조회 구간 계산이 실제로 동작하는지 눈으로 확인하는 용도다.
 * **여기에는 어떤 거래 데이터도 표시하지 않는다.** 표시할 실거래 값이 아직 없기 때문이다.
 */
@Composable
private fun Step1Placeholder(modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val defaultPlan = TradeQueryPlan.of(TradePeriod.DEFAULT, today, RegionCatalog.lawdCodes)
    val maxPlan = TradeQueryPlan.of(TradePeriod.MAX, today, RegionCatalog.lawdCodes)

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("실거래트래커", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Step 1 · 프로젝트 초기화 완료",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "조회 대상 지역 ${RegionCatalog.all.size}곳 " +
                "(서울 ${RegionCatalog.seoul.size} · 성남 ${RegionCatalog.seongnam.size} · " +
                "용인 ${RegionCatalog.yongin.size} · 수원 ${RegionCatalog.suwon.size} · 동탄 1)",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "기본 구간(${TradePeriod.DEFAULT.label}): " +
                "${defaultPlan.range.start} ~ ${defaultPlan.range.endInclusive} " +
                "· 조회 ${defaultPlan.requestCount}회",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "최대 구간(${TradePeriod.MAX.label}): " +
                "${maxPlan.range.start} ~ ${maxPlan.range.endInclusive} " +
                "· 조회 ${maxPlan.requestCount}회",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "기간 선택지: " + TradePeriod.feedOptions.joinToString(" · ") { it.shortLabel },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            DataSourceAttribution.LABEL_NOT_SYNCED,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
