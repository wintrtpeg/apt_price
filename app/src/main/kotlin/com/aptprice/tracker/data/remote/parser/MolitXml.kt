package com.aptprice.tracker.data.remote.parser

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 국토교통부 실거래가 API 응답(XML)을 읽기 위한 DOM 헬퍼.
 *
 * 어노테이션 프로세서 기반 XML 매퍼 대신 DOM 을 직접 쓰는 이유:
 * 필드가 비었을 때 **조용히 기본값으로 채우지 않고 명시적으로 실패 처리**해야 하는데,
 * 그 동작을 매퍼 라이브러리에 맡기면 통제가 어렵다.
 * `javax.xml` 은 JVM 과 Android 양쪽에 있어 순수 단위 테스트도 가능하다.
 */
internal object MolitXml {

    /** XXE(외부 엔티티 주입)를 막은 DocumentBuilderFactory. */
    private fun secureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            // 플랫폼마다 지원하는 feature 가 달라 실패해도 넘어간다.
            // (Android 기본 파서는 일부 Apache 전용 feature 를 모른다)
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching { setXIncludeAware(false) }
            isExpandEntityReferences = false
            isNamespaceAware = false
        }

    fun parse(xml: String): Document =
        secureFactory().newDocumentBuilder()
            // 기본 ErrorHandler 는 잘못된 XML 을 만나면 stderr 에 직접 찍는다.
            // 오류는 예외로 올려 호출부가 다루므로, 로그를 더럽히지 않도록 입을 막는다.
            .apply { setErrorHandler(SilentErrorHandler) }
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    /** 경고는 무시하고 오류는 예외로 올린다. */
    private object SilentErrorHandler : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit
        override fun error(exception: SAXParseException) = throw exception
        override fun fatalError(exception: SAXParseException) = throw exception
    }

    /** 문서 전체에서 태그 이름이 [name] 인 요소들. */
    fun Document.elements(name: String): List<Element> {
        val nodes = getElementsByTagName(name)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** 문서 전체에서 태그 이름이 [name] 인 첫 요소. */
    fun Document.firstElement(name: String): Element? = elements(name).firstOrNull()

    /**
     * 자식 요소의 텍스트. 없거나 비어 있으면 `null`.
     *
     * 국토교통부 응답은 값이 없을 때 태그를 생략하기도 하고 빈 태그로 내려주기도 한다.
     * 둘 다 "값 없음" 으로 똑같이 다룬다.
     */
    fun Element.childText(name: String): String? {
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == name) {
                val text = node.textContent?.trim()
                return text?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /** 자식 요소의 텍스트를 Int 로. 숫자가 아니면 `null`. */
    fun Element.childInt(name: String): Int? = childText(name)?.replace(",", "")?.toIntOrNull()
}
