package com.aptprice.tracker.integrity

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 화면에서 값을 잘못 읽게 만드는 색 선택을 소스 수준에서 막는다.
 *
 * 실기기에서 매매선과 전세선이 구분되지 않았다. 원인은 차트가 Material 스킴의
 * `primary`(매매)와 `secondary`(전세)를 썼는데, 다크 팔레트에서 그 둘이
 * `#8FAAFF` 와 `#8AB4FF` — 사실상 같은 색이었기 때문이다.
 *
 * 색은 테마를 손볼 때마다 조용히 가까워질 수 있다. 그래서 문서가 아니라 테스트로 고정한다.
 * (Compose 를 띄우지 않고 소스의 색 상수를 직접 읽어 검사한다)
 */
class ChartLegibilityTest {

    private val themeDir: File by lazy { findDir("ui/theme") }

    private val colorSource: String by lazy { File(themeDir, "Color.kt").readText() }

    private fun colorOf(name: String): Triple<Int, Int, Int> {
        val match = Regex("""val\s+$name\s*=\s*Color\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)""")
            .find(colorSource)
            ?: error("$name 을 Color.kt 에서 찾지 못했습니다")
        val hex = match.groupValues[1]
        return Triple(
            hex.substring(0, 2).toInt(16),
            hex.substring(2, 4).toInt(16),
            hex.substring(4, 6).toInt(16),
        )
    }

    private fun distance(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val dr = (a.first - b.first).toDouble()
        val dg = (a.second - b.second).toDouble()
        val db = (a.third - b.third).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun `매매색과 전세색은 확실히 다르다`() {
        listOf(
            "라이트" to Pair("SaleBlue", "JeonseTeal"),
            "다크" to Pair("SaleBlueDark", "JeonseTealDark"),
        ).forEach { (mode, pair) ->
            val gap = distance(colorOf(pair.first), colorOf(pair.second))
            // 구분이 안 되던 옛 조합(#8FAAFF vs #8AB4FF)의 거리는 약 11 이었다.
            assertTrue(
                "[$mode] 매매(${pair.first})와 전세(${pair.second})가 너무 비슷하다 (거리 ${gap.toInt()})",
                gap >= MIN_SERIES_GAP,
            )
        }
    }

    @Test
    fun `차트는 매매 전세에 스킴의 primary secondary 를 쓰지 않는다`() {
        // 그 둘은 같은 계열이라 테마를 손보면 언제든 다시 가까워진다.
        // 계열색은 SeriesColors 로만 가져온다.
        val chart = File(findDir("presentation/detail/components"), "PriceChart.kt").readText()
        val body = chart.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")

        assertTrue(
            "차트가 계열색을 SeriesColors 에서 가져오지 않는다",
            body.contains("LocalSeriesColors"),
        )
        listOf("colorScheme.primary", "colorScheme.secondary").forEach { forbidden ->
            assertTrue(
                "차트가 매매/전세 색으로 $forbidden 를 쓰고 있다 — 두 색이 구분되지 않는다",
                !body.contains(forbidden),
            )
        }
    }

    @Test
    fun `매매와 전세는 색 말고 선 모양으로도 구분된다`() {
        // 색을 구별하기 어려운 사람도, 흑백 캡처를 보는 경우도 구분할 수 있어야 한다.
        val chart = File(findDir("presentation/detail/components"), "PriceChart.kt").readText()
        assertTrue(
            "전세선을 파선으로 그리지 않는다 (색만으로 구분하고 있다)",
            chart.contains("dashPathEffect"),
        )
        assertTrue(
            "매매/전세의 선 굵기가 같다",
            Regex("""if\s*\(isSale\)\s*[\d.]+\.dp""").containsMatchIn(chart),
        )
    }

    @Test
    fun `다크 모드 바탕은 중립색이다`() {
        // 바탕이 푸른기를 띠면 화면 전체가 파랗게 보이고 강조색인 파랑이 묻힌다.
        listOf(
            "DarkCanvas",
            "DarkSurface",
            "DarkSurfaceElevated",
            "DarkOutline",
            "DarkHairline",
        ).forEach { name ->
            val (r, _, b) = colorOf(name)
            assertTrue(
                "$name 이 푸른기를 띤다 (R=$r, B=$b) — 바탕은 중립이어야 한다",
                abs(r - b) <= MAX_NEUTRAL_TINT,
            )
        }
    }

    private fun findDir(relative: String): File {
        var dir = File(".").absoluteFile
        repeat(5) {
            listOf("src/main/kotlin/com/aptprice/tracker", "app/src/main/kotlin/com/aptprice/tracker")
                .forEach { base ->
                    val candidate = File(File(dir, base), relative)
                    if (candidate.isDirectory) return candidate
                }
            dir = dir.parentFile ?: return@repeat
        }
        error("$relative 을 찾지 못했습니다 (cwd=${File(".").absolutePath})")
    }

    private companion object {
        /** 두 계열색 사이의 최소 RGB 거리. */
        const val MIN_SERIES_GAP = 80.0

        /** 중립으로 볼 수 있는 R-B 차이의 상한. */
        const val MAX_NEUTRAL_TINT = 6
    }
}
