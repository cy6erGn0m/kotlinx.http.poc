package cg.http

import java.util.HashMap
import kotlin.properties.Delegates
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.text.DateFormat
import java.util.TimeZone
import java.io.OutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import cg.http.util.*
import java.util.regex.Pattern

/**
 * @author Sergey Mashkov
 */

public val http : HttpFactory = HttpFactory()

class HttpFactory {
    fun get() : RequestBuilder<Unit> = RequestBuilder(RequestData())
    fun post() : RequestBuilder<Unit> = RequestBuilder(RequestData("POST"))
}

fun <T> runRequest(attempt : Attempt) : Future<T> {
    val limit = attempt.request.maxAttempts

    if (limit != null && attempt.attempt >= limit) {
        throw IllegalStateException("Attempts exceeded") // TODO
    }

    return failedFuture(UnsupportedOperationException("Not yet implemented"))
}

private class Attempt(val request : RequestData<*>, val attempt : Int = 0)

private data class RequestData<T>(method : String = "GET") {
    val requestHeaders = HashMap<String, String>()
    val parameters = HashMap<String, String>()

    var maxAttempts : Int? = null

    var urlencoded = false
    var outputClosure : (OutputStream) -> Unit = { os -> os.close()}
    var resultClosure : (InputStream) -> T = {null as T}
    var errorClosure : (errors : List<Throwable>) -> Unit = {}

    var method = method
    var host : String by Delegates.notNull()
    var port : Int by Delegates.notNull()
    var path : String = "/"
    var multiPart = false

    var onSuccessClosure : (InputStream) -> T = { s -> s.close(); null}
}

private val hostPortPattern = Pattern.compile("^([a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*):([0-9]+)$")!!
public open class RequestBuilder<T>(public val current : RequestData<T> = RequestData()) {
    private val sdf by Delegates.lazy { httpDateFormat() }

    fun withPath(path : String) : RequestBuilder<T> = with {
        this.path = path
    }

    fun withHost(hostPort : String) : RequestBuilder<T> = with {
        val m = hostPortPattern matcher hostPort
        if (!m.find()) {
            throw IllegalArgumentException("Bad host port spec: $hostPort")
        }

        host = m.group(1)
        port = m.group(m.groupCount()).toInt()

        if (port == 0) {
            throw IllegalArgumentException("Bad port: $port")
        }
    }

    fun withHost(host : String, port : Int) : RequestBuilder<T> = with {
        require(port >= 0) {"Port should be positive but it is $port"}
        require(host matches "[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*") {"host seems to be not valid: $host"}

        this.host = host
        this.port = port
    }

    fun withHeader(headerName : String, headerValue : String) = with {
        require(headerName.matches("[a-zA-Z0-9_\\-]+")) {"header name seems to be not valid: $headerName"}
        require(headerValue.matches("[a-zA-Z0-9_+;#,. %\\-]+")) {"header value seems to be not valid: $headerValue"}

        requestHeaders[headerName] = headerValue
    }

    fun withHeader(headerName : String, headerValue : Number) = withHeader(headerName, headerValue.toString())
    fun withHeader(headerName : String, headerValue : Boolean) = withHeader(headerName, headerValue.toString())
    fun withHeader(headerName : String, headerValue : Date) = withHeader(headerName, sdf.format(headerValue))

    fun withRetries(retries : Int) = with {
        require(retries > 0) {"retries count should be positive"}
        maxAttempts = retries
    }

    fun urlEncoded() : RequestBuilder<T> = with {
        urlencoded = true
    }

    fun multiPart() : RequestBuilder<T> = with {
        multiPart = true
        outputClosure = { os ->
            // TODO construct multiPart
            os.close()
        }
    }

    fun withRequestStream(block : (OutputStream) -> Unit) = with {
        outputClosure = block
    }

    fun withRequestTextBody(charset : String = "UTF-8", block : () -> String) : RequestBuilder<T> = with {
        outputClosure = { os ->
            os.write(block().toByteArray(charset))
            os.close()
        }
    }

    fun withRequestTextBody(text : String, charset : String = "UTF-8") : RequestBuilder<T> = withRequestTextBody(charset) {text}

    fun withRequestBinaryBody(block : () -> ByteArray) : RequestBuilder<T> = with {
        outputClosure = { os ->
            os.write(block())
            os.close()
        }
    }

    fun withRequestNioBinaryBody(block : () -> ByteBuffer) : RequestBuilder<T> = with {
        outputClosure = { os ->
            os.write(block())
            os.close()
        }
    }

    [suppress("UNCHECKED_CAST")]
    fun <V> onSuccess(block : (InputStream) -> V) : RequestBuilder<V> {
        val next = current : RequestData<*> as RequestData<V>

        next.onSuccessClosure = block
        return this : RequestBuilder<*> as RequestBuilder<V>
    }

    fun onError(block : (errors : List<Throwable>) -> Unit) : RequestBuilder<T> = with {
        errorClosure = block
    }

    fun send() : Future<T> = runRequest(Attempt(current))

    protected inline fun with(block : RequestData<T>.() -> Unit) : RequestBuilder<T> {
        current.block()
        return this
    }

}
