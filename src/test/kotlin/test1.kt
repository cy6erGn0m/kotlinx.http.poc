package cg.http

import org.junit.Test as test
import org.junit.Ignore as ignore
import kotlin.test.assertEquals
import kotlin.test.expect

/**
 * Created by CG on 28.02.2015.
 */

class MainTest {
    ignore
    test fun main() {
        val request = http.get() withHost "google.ru:80" withPath "/" withRetries 3 onSuccess { i, s ->
            s.buffered().reader(i.contentCharset ?: Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } onError { errors ->
            errors.forEach {
                println("Bad: $it")
            }
        }

        val f = request.withParam("q", "where is").send()
        val text = f.get()
        println(text)
        println()
        println("Size is ${text.length()}")
    }

    test fun testContentCharset() {
        assertEquals("UTF-8", getContentCharset(mapOf("Content-Type" to listOf("text/html; charset=UTF-8"))))
        assertEquals("UTF-8", getContentCharset(mapOf("Content-Type" to listOf("text/html; charset=UTF-8 "))))
        assertEquals("UTF-8", getContentCharset(mapOf("Content-Type" to listOf("text/html; charset='UTF-8'"))))
        assertEquals("UTF-8", getContentCharset(mapOf("Content-Type" to listOf("text/html; charset=\"UTF-8\""))))
        assertEquals("UTF-8", getContentCharset(mapOf("Content-Type" to listOf("charset=UTF-8"))))
        assertEquals("UTF-9", getContentCharset(mapOf("Content-Type" to listOf("charset=UTF-8;charset=UTF-9"))))
        assertEquals("UTF-9", getContentCharset(mapOf("Content-Type" to listOf("charset=UTF-8", "charset=UTF-9"))))
        assertEquals("UTF-9", getContentCharset(mapOf("Content-Type" to listOf("charset=UTF-8", "text/html; charset=UTF-9"))))
    }

    test fun testContentType() {
        assertEquals("text/html", getContentType(mapOf("Content-Type" to listOf("text/html; charset=UTF-8"))))
        assertEquals("text/html", getContentType(mapOf("Content-Type" to listOf("text/html;charset=UTF-8"))))
        assertEquals("text/xml+svg", getContentType(mapOf("Content-Type" to listOf("text/xml+svg;charset=UTF-8"))))
        assertEquals("text/html", getContentType(mapOf("Content-Type" to listOf("text/html ; charset=UTF-8"))))
        assertEquals(null, getContentType(mapOf("Content-Type" to listOf("charset=UTF-8"))))
    }

    test fun buildURLSimple() {
        assertEquals("http://test:8080/", buildUrl(requestOf().with {host = "test"; port = 8080; path = "/"}))
        assertEquals("http://test:8080/", buildUrl(requestOf().with {host = "test"; port = 8080; path = "//"}))
        assertEquals("http://test:8080/", buildUrl(requestOf().with {host = "test"; port = 8080; path = ""}))
    }

    test fun buildURLSimpleWithParams() {
        assertEquals("http://test:8080/?p1=v1", buildUrl(requestOf(mapOf("p1" to "v1")).with {host = "test"; port = 8080; path = "/"}))
        assertEquals("http://test:8080/myServlet?p1=v1", buildUrl(requestOf(mapOf("p1" to "v1")).with {host = "test"; port = 8080; path = "/myServlet"}))
        assertEquals("http://test:8080/myServlet?p1=v1", buildUrl(requestOf(mapOf("p1" to "v1")).with {host = "test"; port = 8080; path = "//myServlet"}))
    }

    test fun buildURLEscapeParam() {
        assertEquals("http://test:8080/?p1+space=v1+space", buildUrl(requestOf(mapOf("p1 space" to "v1 space")).with {host = "test"; port = 8080; path = "/"}))
        assertEquals("http://test:8080/?p1%3Fspace=v1+space", buildUrl(requestOf(mapOf("p1?space" to "v1 space")).with {host = "test"; port = 8080; path = "/"}))
    }

    test fun buildURLPathWithParams() {
        assertEquals("http://test:8080/myServlet?p0=v0&p1=v1", buildUrl(requestOf(mapOf("p1" to "v1")).with {host = "test"; port = 8080; path = "/myServlet?p0=v0"}))
        assertEquals("http://test:8080/myServlet?p0=v0&p1=v1", buildUrl(requestOf(mapOf("p1" to "v1")).with {host = "test"; port = 8080; path = "/myServlet?p0=v0&"}))
        assertEquals("http://test:8080/myServlet?p1=v1", buildUrl(requestOf(mapOf("p1" to "v1")).with {host = "test"; port = 8080; path = "/myServlet?"}))
    }

    test fun buildURLSSL() {
        assertEquals("https://test:8080/", buildUrl(requestOf().with {host = "test"; port = 8080; path = "/"; https = true}))
    }

    private fun requestOf(params : Map<String, String> = emptyMap()) = RequestData<Unit>("GET").with {
        parameters.putAll(params)
    }
}