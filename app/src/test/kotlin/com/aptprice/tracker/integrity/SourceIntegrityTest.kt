package com.aptprice.tracker.integrity

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * 운영 코드(src/main)에 **지어낸 거래 데이터가 섞여 들지 않도록** 소스를 직접 검사한다.
 *
 * 작업지시서 2.2 의 "임의의 가짜 데이터 생성 절대 금지" 는 문서로만 두면 지켜지지 않는다.
 * 나중에 누군가 미리보기용 샘플 단지나 난수 가격을 넣으면 이 테스트가 실패한다.
 */
class SourceIntegrityTest {

    private val mainSources: List<File> by lazy {
        val root = findMainSourceRoot()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `운영 소스를 찾을 수 있다`() {
        assertTrue("검사할 소스가 없으면 이 테스트는 의미가 없다", mainSources.size > 20)
    }

    @Test
    fun `운영 코드에 난수가 없다`() {
        // 난수로 만든 값이 화면에 뜨면 그것이 곧 가짜 실거래가다.
        val pattern = Regex("""\b(kotlin\.random\.Random|java\.util\.Random|Math\.random)\b|\bRandom\(""")
        assertNoMatch(pattern, "난수 생성")
    }

    @Test
    fun `운영 코드에 mock dummy fake sample 식별자가 없다`() {
        // 주석과 문자열을 뺀 코드에서만 찾는다.
        //
        // 'placeholder' 는 뺐다. Compose 의 OutlinedTextField 등이 정식 파라미터 이름으로
        // 쓰기 때문에 오탐이 계속 난다. 오탐이 잦은 검사는 결국 꺼지게 되므로,
        // 가짜 데이터를 실제로 시사하는 단어만 남긴다.
        val pattern = Regex("""\b(mock|dummy|fake|sample|lorem)""", RegexOption.IGNORE_CASE)
        assertNoMatch(pattern, "가짜 데이터를 시사하는 식별자")
    }

    @Test
    fun `운영 코드가 테스트 픽스처를 읽지 않는다`() {
        // src/test/resources/molit 의 XML 은 파서 테스트 전용이다.
        val pattern = Regex("""molit/|test/resources|getResourceAsStream""")
        assertNoMatch(pattern, "테스트 픽스처 참조")
    }

    @Test
    fun `운영 코드에 Compose 미리보기가 없다`() {
        // @Preview 는 샘플 데이터가 스며드는 대표적인 통로다.
        // 꼭 필요하면 debug 소스셋으로 분리하고 이 테스트의 대상에서 빼야 한다.
        assertNoMatch(Regex("""@Preview"""), "Compose 미리보기")
    }

    @Test
    fun `값을 채워 넣는 기본값 API 를 두지 않는다`() {
        val tradeValue = mainSources.single { it.name == "TradeValue.kt" }.readText()
        listOf("orElse", "getOrDefault", "valueOrZero", "orZero").forEach { forbidden ->
            assertFalse(
                "TradeValue 에 $forbidden 이 생기면 값 없음을 조용히 메울 수 있다",
                tradeValue.contains(forbidden),
            )
        }
    }

    @Test
    fun `출처 문구가 한곳에 모여 있다`() {
        // 화면마다 문구를 따로 적으면 하나가 빠져도 알아채기 어렵다.
        val offenders = codeLines()
            .filter { (file, line) ->
                file.name != "DataSourceAttribution.kt" &&
                    file.name != "strings.xml" &&
                    line.contains("국토교통부 실거래가 공개시스템")
            }
        assertTrue(
            "출처 문구는 DataSourceAttribution 에만 두어야 한다: " +
                offenders.joinToString { "${it.first.name}: ${it.second.trim()}" },
            offenders.isEmpty(),
        )
    }

    /** 주석과 문자열 리터럴을 걷어낸 코드 줄만 훑는다. */
    private fun codeLines(): List<Pair<File, String>> = mainSources.flatMap { file ->
        var inBlockComment = false
        file.readLines().mapNotNull { raw ->
            var line = raw
            if (inBlockComment) {
                val end = line.indexOf("*/")
                if (end < 0) return@mapNotNull null
                inBlockComment = false
                line = line.substring(end + 2)
            }
            val blockStart = line.indexOf("/*")
            if (blockStart >= 0) {
                val end = line.indexOf("*/", blockStart)
                if (end < 0) {
                    inBlockComment = true
                    line = line.substring(0, blockStart)
                } else {
                    line = line.substring(0, blockStart) + line.substring(end + 2)
                }
            }
            line = line.substringBefore("//")
            if (line.isBlank()) null else file to line
        }
    }

    private fun assertNoMatch(pattern: Regex, what: String) {
        val offenders = codeLines()
            .filter { (_, line) -> pattern.containsMatchIn(stripStrings(line)) }
            .map { (file, line) -> "${file.name}: ${line.trim()}" }
        assertTrue("운영 코드에 $what 이(가) 있습니다:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** 큰따옴표 문자열 안의 내용은 검사 대상에서 뺀다. */
    private fun stripStrings(line: String): String =
        line.replace(Regex(""""(\\.|[^"\\])*""""), "\"\"")

    private fun findMainSourceRoot(): File {
        var dir = File(".").absoluteFile
        repeat(5) {
            listOf("src/main/kotlin", "app/src/main/kotlin").forEach { candidate ->
                val file = File(dir, candidate)
                if (file.isDirectory) return file
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("src/main/kotlin 을 찾지 못했습니다 (cwd=${File(".").absolutePath})")
    }
}
