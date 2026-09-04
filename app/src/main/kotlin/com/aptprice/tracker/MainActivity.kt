package com.aptprice.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aptprice.tracker.core.crash.CrashReporter
import com.aptprice.tracker.core.crash.CrashReportScreen
import com.aptprice.tracker.presentation.navigation.AppNavigation
import com.aptprice.tracker.ui.theme.AptPriceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AptPriceTheme {
                // 지난 실행이 비정상 종료됐으면 앱을 띄우기 전에 그 이유부터 보여 준다.
                // 곧바로 화면을 열면 같은 이유로 다시 죽어 내용을 볼 수 없다.
                var report by remember { mutableStateOf(CrashReporter.read(this)) }
                val pending = report
                if (pending != null) {
                    CrashReportScreen(
                        report = pending,
                        onDismiss = {
                            CrashReporter.clear(this)
                            report = null
                        },
                    )
                } else {
                    AppNavigation()
                }
            }
        }
    }
}
