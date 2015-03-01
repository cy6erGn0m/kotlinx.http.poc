package cg.http.test

import org.junit.Test as test
import org.junit.Ignore as ignore
import kotlin.test.assertEquals
import kotlin.test.expect
import java.net.HttpURLConnection
import cg.http.*
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotEquals
import java.util.Date
import java.text.SimpleDateFormat
import org.mockito.InOrder
import java.util.TimeZone

/**
 * Created by CG on 28.02.2015.
 */

class MainTest {
    ignore
    test fun main() {
//        val request = http.get() withHost "google.ru:80" withPath "/" withRetries 3 onSuccess { i, s ->
//            s.buffered().reader(i.contentCharset ?: Charsets.UTF_8).use { reader ->
//                reader.readText()
//            }
//        } onError { errors ->
//            errors.forEach {
//                println("Bad: $it")
//            }
//        }
//
//        val f = request.withParam("q", "where is").send()
//        val text = f.get()
//        println(text)
//        println()
//        println("Size is ${text.length()}")

        val head = http.head() withHost "google.ru:80" withPath "/"
        val headInfo = head.send().get()

        println(headInfo)
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
}

class RequestAttemptHandlerTest {
    test fun testDummy() {
        val request = requestOf().with {host = "test"; port = 80}
        handleRequestAttempt(Attempt(request)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray()))
            connection
        }
    }

    test fun testSimple() {
        var result : String? = null
        val request = requestOf().with {host = "test"; port = 80; onSuccessClosure = {ri, s -> result = s.reader(ri.contentCharset ?: Charsets.UTF_8).buffered().readText()}}
        val result2 = handleRequestAttempt(Attempt(request)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray()))
            connection
        }

        assertEquals("test content", result)
        assertEquals(Unit, result2)
    }

    test fun testSimpleWithBuilder() {
        var result : String? = null
        val request = http.get().withHost("test:80").onSuccess { (ri, s) ->
            result = s.reader(ri.contentCharset ?: Charsets.UTF_8).buffered().readText()
            result
        }

        val result2 = handleRequestAttempt(Attempt(request.current)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray()))
            connection
        }

        assertEquals("test content", result)
        assertEquals("test content", result2)
    }

    test fun testSimpleWithBuilderLightText() {
        val request = http.get().withHost("test:80").withTextResponse()

        val result = handleRequestAttempt(Attempt(request.current)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray()))
            connection
        }

        assertEquals("test content", result)
    }

    test fun testSimpleWithBuilderLightTextWithBadEncoding() {
        val request = http.get().withHost("test:80").withTextResponse()

        val result = handleRequestAttempt(Attempt(request.current)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("Тест".toByteArray("Windows-1251")))
            connection
        }

        assertNotEquals("Тест", result)
    }

    test fun testSimpleWithBuilderLightTextWithGoodEncoding() {
        val request = http.get().withHost("test:80").withTextResponse()

        val result = handleRequestAttempt(Attempt(request.current)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("Тест".toByteArray("Windows-1251")))
            Mockito.`when`(connection.getHeaderFields()).thenReturn(mapOf("Content-Type" to listOf("text/plain; charset=windows-1251")))
            connection
        }

        assertEquals("Тест", result)
    }

    test fun testSimpleWithBuilderLightBytes() {
        val request = http.get().withHost("test:80").withBytesResponse()

        val result = handleRequestAttempt(Attempt(request.current)) { url, proxy ->
            val connection = Mockito.mock(javaClass<HttpURLConnection>())
            Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray()))
            connection
        }

        assertEquals("test content", result.toString("UTF-8"))
    }

    test fun testHeaders() {
        val sdf = SimpleDateFormat("yyyyMMdd")
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"))

        val request = http.get().withHost("test:8080").
                withHeader("My-Header-1", "string").
                withHeader("My-Header-2", 2).
                withHeader("My-Header-3", true).
                withHeader("My-Header-4", sdf.parse("2014-01-01"))


        val connection = Mockito.mock(javaClass<HttpURLConnection>())
        Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray()))

        val result = handleRequestAttempt(Attempt(request.current)) { url, proxy -> connection }

        Mockito.verify(connection, Mockito.times(1)).addRequestProperty("Connection", "close")
        Mockito.verify(connection, Mockito.times(1)).addRequestProperty("My-Header-1", "string")
        Mockito.verify(connection, Mockito.times(1)).addRequestProperty("My-Header-2", "2")
        Mockito.verify(connection, Mockito.times(1)).addRequestProperty("My-Header-3", "true")
        Mockito.verify(connection, Mockito.times(1)).addRequestProperty("My-Header-4", "Sun, 01 Dec 2013 00:00:00 GMT")
    }

    test fun testReceiveResponseHeaders() {
        val request = http.get().withHost("test:8080").
                onSuccessText { (responseInfo, s) -> responseInfo }

        val connection = Mockito.mock(javaClass<HttpURLConnection>())
        Mockito.`when`(connection.getInputStream()).thenReturn(ByteArrayInputStream("test content".toByteArray("UTF-8")))
        Mockito.`when`(connection.getHeaderFields()).thenReturn(mapOf(
                "Content-Type" to listOf("text/plain;charset=utf-8"),
                "My-Header" to listOf("my value")
        ))
        Mockito.`when`(connection.getResponseCode()).thenReturn(200)
        Mockito.`when`(connection.getResponseMessage()).thenReturn("OK")

        val result = handleRequestAttempt(Attempt(request.current)) { url, proxy -> connection }

        Mockito.verify(connection).getHeaderFields()
        assertEquals(Charsets.UTF_8, result.contentCharset)
        assertEquals("text/plain", result.contentType)
        assertEquals(listOf("my value"), result.headers["My-Header"])
        assertEquals(200, result.code)
        assertEquals("OK", result.message)
    }
}

fun requestOf(params : Map<String, String> = emptyMap()) = RequestData<Unit>("GET").with {
    parameters.putAll(params)
}

inline fun <T> RequestData<T>.with(block : RequestData<T>.() -> Unit) : RequestData<T> {
    this.block()
    return this
}
