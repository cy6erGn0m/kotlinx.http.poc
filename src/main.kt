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

/**
 * @author Sergey Mashkov
 */

private class Attempt

private data class RequestData<T> {
    val requestHeaders = HashMap<String, String>()
    val parameters = HashMap<String, String>()

    var maxAttempts : Int? = null

    var urlencoded = false
    var outputClosurePresent = false
    var outputClosure : (OutputStream) -> Unit = {}
    var resultClosure : (InputStream) -> T = {null as T}
    var errorClosure : (errors : List<Throwable>) -> Unit = {}

    var method = "GET"
    var host : String by Delegates.notNull()
    var port : Int by Delegates.notNull()
    var path : String = "/"
}

public open class RequestBuilder<T>(public val current : RequestData<T> = RequestData()) {
    private val sdf by Delegates.lazy { makeDateFormat() }

    fun withPath(path : String) : RequestBuilder<T> = with {
        this.path = path
    }

    fun withHost(host : String, port : Int) : RequestBuilder<T> = with {
        require(port >= 0) {"Port should be positive but it is $port"}
        require(host matches "[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)") {"host seems to be not valid: $host"}

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

    fun urlEncoded() : RequestBuilderWithStream<T> {
        val s = RequestBuilderWithStream(current)
        s.current.outputClosurePresent = true
        s.current.urlencoded = true
        return s
    }

    fun multipart() : RequestBuilderMultipart<T> {
        val s = RequestBuilderMultipart(current)
        s.current.outputClosurePresent = true
        return s
    }

    fun onError(block : (errors : List<Throwable>) -> Unit) : RequestBuilder<T> = with {
        errorClosure = block
    }

    protected inline fun with(block : RequestData<T>.() -> Unit) : RequestBuilder<T> {
        current.block()
        return this
    }

    private fun makeDateFormat() : DateFormat {
        val sdf = SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"))
        return sdf
    }
}

// TODO: urlencoded filter

public class RequestBuilderWithStream<T>(current : RequestData<T>) : RequestBuilder<T>(current) {
    fun withRequestStream(block : (OutputStream) -> Unit) = with {
        outputClosure = block
        outputClosurePresent = true
    }

    fun withRequestTextBody(charset : String = "UTF-8", block : () -> String) : RequestBuilderWithStream<T> {
        current.outputClosurePresent = true
        current.outputClosure = { os ->
            os.write(block().toByteArray(charset))
            os.close()
        }
        return this
    }

    fun withRequestBinaryBody(block : () -> ByteArray) : RequestBuilderWithStream<T> {
        current.outputClosurePresent = true
        current.outputClosure = { os ->
            os.write(block())
            os.close()
        }
        return this
    }

    fun withRequestNioBinaryBody(block : () -> ByteBuffer) : RequestBuilderWithStream<T> {
        current.outputClosurePresent = true
        current.outputClosure = { os ->
            val bb = block()

            if (bb.hasArray()) {
                os.write(bb.array(), bb.arrayOffset(), bb.remaining())
                bb.position(bb.position() + bb.remaining())
            } else {
                val temp = ByteArray(bb.remaining())
                bb.get(temp)
                os.write(temp)
            }

            os.close()
        }
        return this
    }

}

public class RequestBuilderMultipart<T>(current : RequestData<T>) : RequestBuilder<T>(current) {
    {
        current.outputClosure = { os ->
            // TODO construct multipart
        }
    }
}