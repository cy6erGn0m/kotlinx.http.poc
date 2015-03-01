package cg.http

import java.net.URLEncoder
import java.net.URL
import java.net.HttpURLConnection
import java.util.regex.Pattern
import cg.http.util.matcher
import cg.http.util.findAll
import cg.http.util.toCharset
import java.nio.charset.Charset
import cg.http.util.unquote
import javax.net.ssl.HttpsURLConnection
import java.net.Proxy

data class ResponseInfo(val code : Int, val message : String?, val headers : Map<String, List<String>>, val contentType : String?, val contentCharset : Charset?)

/**
 * Created by CG on 28.02.2015.
 */

private fun String.withNoStartSlash() : String = if (isEmpty()) this else
    if (charAt(0) == '/') this.substring(1).withNoStartSlash() else
        this

private fun String.encode() = URLEncoder.encode(this, "UTF-8")

private fun urlParamsImpl(request : RequestData<*>) = request.parameters.entrySet().map { it.key.encode() + "=" + it.value.encode() }.joinToString("&")

private fun urlParams(request : RequestData<*>) = if (request.urlencoded || request.multiPart) "" else urlParamsImpl(request)

private fun urlPathParamsSeparator(request : RequestData<*>) =  if (request.urlencoded || request.multiPart || request.parameters.isEmpty()) "" else
    if (request.path.contains("?") && !request.path.endsWith("?") && !request.path.endsWith("&")) "&" else
        if (request.path.endsWith("&") || request.path.endsWith("?")) "" else
        "?"

fun buildUrl(request : RequestData<*>) = "http${if (request.https) "s" else ""}://${request.host}:${request.port}/${request.path.withNoStartSlash()}${urlPathParamsSeparator(request)}${urlParams(request)}"

private val parameterPattern = Pattern.compile("[a-zA-z\\-_0-9]+=.*")
private val charsetPattern = Pattern.compile("charset=([^\\s;]+)")
private val contentTypePattern = Pattern.compile("^([^\\s;]+)")
fun getContentCharset(headers : Map<String?, List<String>>) : String? =
        headers.filterKeys { it == "Content-Type" }.values().
                flatMap { e -> e }.filter { it.contains("charset=") }.
                flatMap {it.matcher(charsetPattern).findAll()}.lastOrNull()?.group(1)?.unquote()

fun getContentType(headers : Map<String?, List<String>>) : String? =
        headers.filterKeys { it == "Content-Type" }.values().
                flatMap {it.flatMap {it.matcher(contentTypePattern).findAll()}}.
                map {it.group(1)}.filter { it != null && it.isNotEmpty() && !parameterPattern.matcher(it).find() }.lastOrNull()

fun defaultHttpConnectionFactory(url : URL, proxy : Proxy) : HttpURLConnection = url.openConnection(proxy) as HttpURLConnection

public fun <T> handleRequestAttempt(attempt : Attempt<T>, httpConnectionFactory : (URL, Proxy) -> HttpURLConnection = ::defaultHttpConnectionFactory) : T {
    // TODO proxy
    // TODO think of chunk mode
    val connection = httpConnectionFactory(URL(buildUrl(attempt.request)), Proxy.NO_PROXY)
    try {
        if (connection is HttpsURLConnection && attempt.request.ignoreSSLCertErrors) {
//            connection.setSSLSocketFactory()
            // TODO implement ignoring socket factory, etc
        }
        connection.setAllowUserInteraction(false)
        connection.setRequestMethod(attempt.request.method)
        connection.setInstanceFollowRedirects(true)
        connection.setDoOutput(true)
        connection.setDoInput(true)
        connection.addRequestProperty("Connection", "close")

        attempt.request.requestHeaders.forEach { k, v ->
            connection.addRequestProperty(k, v)
        }

        if (attempt.request.outputClosureSet) {
            attempt.request.outputClosure(connection.getOutputStream())
        }

        val headers = connection.getHeaderFields()
        headers.forEach { e ->
            e.getValue().forEach {
                println("${e.getKey()}: $it")
            }
        }

        val ri = ResponseInfo(code = connection.getResponseCode(), message = connection.getResponseMessage(),
                contentCharset = getContentCharset(headers)?.toCharset(),
                headers = headers,
                contentType = getContentType(headers)
                )
        return attempt.request.onSuccessClosure(ri, connection.getInputStream())
    } catch (e : Throwable) {
        println("Failed: ${connection.getResponseCode()} ${connection.getResponseMessage()}")
        // TODO next attempt
        attempt.request.errorClosure(listOf(e))
        throw e
    } finally {
        connection.disconnect()
    }
}