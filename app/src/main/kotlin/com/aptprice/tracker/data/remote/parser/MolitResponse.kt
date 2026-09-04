package com.aptprice.tracker.data.remote.parser

import com.aptprice.tracker.data.remote.parser.MolitXml.childText
import com.aptprice.tracker.data.remote.parser.MolitXml.elements
import com.aptprice.tracker.data.remote.parser.MolitXml.firstElement
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * 한 행을 읽지 못한 사유. 조용히 버리지 않고 개수와 사유를 남긴다.
 *
 * 값이 없는데 임의로 채워 넣으면 화면에 가짜 실거래가가 뜨게 되므로,
 * 읽을 수 없는 행은 아예 목록에서 빼되 **몇 건을 왜 뺐는지는 반드시 기록**한다.
 */
data class RowParseFailure(
    val reason: String,
    /** 문제가 된 행의 원문(디버깅용, 최대 500자) */
    val rawRow: String,
)

/** 응답 한 건의 파싱 결과. */
data class MolitPage<T>(
    val items: List<T>,
    val failures: List<RowParseFailure>,
    /** 서버가 알려준 전체 건수. 페이징 판단에 쓴다. */
    val totalCount: Int,
    val pageNo: Int,
    val numOfRows: Int,
) {
    /** 아직 받아오지 않은 페이지가 남았는가. */
    val hasMorePages: Boolean
        get() = numOfRows > 0 && pageNo * numOfRows < totalCount
}

/**
 * 국토교통부 실거래가 응답의 공통 껍데기를 읽는다.
 * 실제 행(item) 해석은 [AptTradeParser] / [AptRentParser] 가 맡는다.
 */
internal object MolitResponse {

    private const val MAX_RAW_ROW_LENGTH = 500

    /**
     * 응답을 파싱한다.
     *
     * @throws MolitApiException 조회 결과 없음을 제외한 모든 오류
     * @return 결과가 없으면 빈 [MolitPage]
     */
    fun <T> parse(xml: String, rowParser: (Element) -> Result<T>): MolitPage<T> {
        val document = runCatching { MolitXml.parse(xml) }.getOrElse {
            throw MolitApiException(
                MolitApiError(
                    code = "PARSE",
                    message = "응답 XML 을 해석하지 못했습니다: ${it.message}",
                    kind = MolitApiError.Kind.SERVICE_ERROR,
                ),
            )
        }

        gatewayError(document)?.let { throw MolitApiException(it) }
        serviceError(document)?.let { error ->
            if (error.kind == MolitApiError.Kind.NO_DATA) return emptyPage()
            throw MolitApiException(error)
        }

        val items = mutableListOf<T>()
        val failures = mutableListOf<RowParseFailure>()
        document.elements("item").forEach { row ->
            rowParser(row).fold(
                onSuccess = { items += it },
                onFailure = { failures += RowParseFailure(it.message ?: "알 수 없는 오류", row.raw()) },
            )
        }

        val body = document.firstElement("body")
        val totalCount = body?.childText("totalCount")?.toIntOrNull()
        val pageNo = body?.childText("pageNo")?.toIntOrNull() ?: 1
        val numOfRows = body?.childText("numOfRows")?.toIntOrNull() ?: items.size

        return MolitPage(
            items = items,
            failures = failures,
            // totalCount 가 없으면 이번에 읽은 만큼만 있는 것으로 본다.
            totalCount = totalCount ?: (items.size + failures.size),
            pageNo = pageNo,
            numOfRows = numOfRows,
        )
    }

    private fun <T> emptyPage() = MolitPage<T>(
        items = emptyList(),
        failures = emptyList(),
        totalCount = 0,
        pageNo = 1,
        numOfRows = 0,
    )

    /** `<response><header><resultCode>` 형태의 서비스 응답 코드. */
    private fun serviceError(document: Document): MolitApiError? {
        val header = document.firstElement("header") ?: return null
        val code = header.childText("resultCode")?.trim() ?: return null
        if (code in MolitApiError.SUCCESS_CODES) return null
        val message = header.childText("resultMsg") ?: "resultCode=$code"
        return MolitApiError(
            code = code,
            message = message,
            kind = if (code in MolitApiError.NO_DATA_CODES) {
                MolitApiError.Kind.NO_DATA
            } else {
                MolitApiError.kindOf(code)
            },
        )
    }

    /**
     * `<OpenAPI_ServiceResponse><cmmMsgHeader>` 형태의 포털 게이트웨이 오류.
     * 인증키 오류·트래픽 초과는 서비스에 닿기 전에 여기서 막힌다.
     */
    private fun gatewayError(document: Document): MolitApiError? {
        val header = document.firstElement("cmmMsgHeader") ?: return null
        val code = header.childText("returnReasonCode")?.trim() ?: return null
        val message = header.childText("returnAuthMsg")
            ?: header.childText("errMsg")
            ?: "returnReasonCode=$code"
        return MolitApiError(
            code = code,
            message = message,
            kind = MolitApiError.kindOf(code),
        )
    }

    private fun Element.raw(): String =
        textContent.orEmpty().replace(Regex("\\s+"), " ").trim().take(MAX_RAW_ROW_LENGTH)
}
