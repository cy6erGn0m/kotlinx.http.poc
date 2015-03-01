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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ArrayBlockingQueue
import java.nio.charset.Charset

/**
 * @author Sergey Mashkov
 */

public val http : HttpFactory = HttpFactory()
private val exec = ThreadPoolExecutor(0, 32, 10, TimeUnit.SECONDS, ArrayBlockingQueue(1024))

class HttpFactory {
    fun get() : RequestBuilder<ResponseInfo> = RequestBuilder(RequestData())
    fun post() : RequestBuilder<ResponseInfo> = RequestBuilder(RequestData("POST"))
    fun head() : RequestBuilder<ResponseInfo> = RequestBuilder(RequestData("HEAD"))
    fun put() : RequestBuilder<ResponseInfo> = RequestBuilder(RequestData("PUT"))
    fun delete() : RequestBuilder<ResponseInfo> = RequestBuilder(RequestData("DELETE"))
    fun method(method : String) : RequestBuilder<ResponseInfo> = RequestBuilder(RequestData(method))
}

fun <T> runRequest(attempt : Attempt<T>) : Future<T> {
    val limit = attempt.request.maxAttempts

    if (limit != null && attempt.attempt >= limit) {
        throw IllegalStateException("Attempts exceeded") // TODO
    }

    return exec.submit<T> {
        handleRequestAttempt(attempt)
    }
}

class Attempt<T>(val request : RequestData<T>, val attempt : Int = 0)

data class RequestData<T>(method : String = "GET") {
    val requestHeaders = HashMap<String, String>()
    val parameters = HashMap<String, String>()

    var maxAttempts : Int? = null

    var outputClosureSet = false
    var outputClosure : (OutputStream) -> Unit = { os -> os.close()}
        set(newOC) {
            $outputClosure = newOC
            outputClosureSet = true
        }
    var errorClosure : (errors : List<Throwable>) -> Unit = {}

    var method = method
    var host : String by Delegates.notNull()
    var port : Int by Delegates.notNull()
    var path : String = "/"
    var urlencoded = false
    var multiPart = false
    var https = false
    var ignoreSSLCertErrors = false

    var onSuccessClosure : (ResponseInfo, InputStream) -> T = [suppress("UNCHECKED_CAST")]{ r, s -> s.close(); r as T}
}

private val hostPortPattern = Pattern.compile("^([a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*):([0-9]+)$")!!
public open class RequestBuilder<T>(public val current : RequestData<T> = RequestData()) {
    private val sdf by Delegates.lazy { httpDateFormat() }

    fun withSSL(ignoreSSLErrors: Boolean) = with {
        https = true
        ignoreSSLCertErrors = ignoreSSLErrors
    }

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
        require(headerValue.matches("[a-zA-Z0-9_+;:#,. %\\-]+")) {"header value seems to be not valid: $headerValue"}

        requestHeaders[headerName] = headerValue
    }

    fun withHeader(headerName : String, headerValue : Number) = withHeader(headerName, headerValue.toString())
    fun withHeader(headerName : String, headerValue : CharSequence) = withHeader(headerName, headerValue.toString())
    fun withHeader(headerName : String, headerValue : Boolean) = withHeader(headerName, headerValue.toString())
    fun withHeader(headerName : String, headerValue : Date) = withHeader(headerName, sdf.format(headerValue))

    fun withParam(param : String, value : String) = with {
        parameters[param] = value
    }

    fun withParam(param : String, value : Number) = withParam(param, value.toString())
    fun withParam(param : String, value : Boolean) = withParam(param, value.toString())
    fun withParam(param : String, value : CharSequence) = withParam(param, value.toString())

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
    fun <V> onSuccess(block : (ResponseInfo, InputStream) -> V) : RequestBuilder<V> {
        val next = current : RequestData<*> as RequestData<V>

        next.onSuccessClosure = block
        return this : RequestBuilder<*> as RequestBuilder<V>
    }

    [suppress("UNCHECKED_CAST")]
    fun <V> onSuccessText(block : (ResponseInfo, String) -> V) : RequestBuilder<V> {
        val next = current : RequestData<*> as RequestData<V>

        next.onSuccessClosure = { ri, s -> block(ri, s.buffered().reader(ri.contentCharset ?: Charsets.UTF_8).readText()) }
        return this : RequestBuilder<*> as RequestBuilder<V>
    }

    [suppress("UNCHECKED_CAST")]
    fun <V> onSuccessBytes(block : (ResponseInfo, ByteArray) -> V) : RequestBuilder<V> {
        val next = current : RequestData<*> as RequestData<V>

        next.onSuccessClosure = { ri, s -> block(ri, s.readBytes()) } // TODO use estimated size
        return this : RequestBuilder<*> as RequestBuilder<V>
    }

    fun withTextResponse() : RequestBuilder<String> = onSuccessText { (responseInfo, s) -> s }
    fun withBytesResponse() : RequestBuilder<ByteArray> = onSuccessBytes { (responseInfo, bytes) -> bytes }

    fun onError(block : (errors : List<Throwable>) -> Unit) : RequestBuilder<T> = with {
        errorClosure = block
    }

    fun send() : Future<T> = runRequest(Attempt(current))

    protected inline fun with(block : RequestData<T>.() -> Unit) : RequestBuilder<T> {
        current.block()
        return this
    }

}
