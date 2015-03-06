package cg.http.util

import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import java.util.regex.Matcher
import java.util.ArrayList
import java.util.regex.MatchResult
import java.nio.charset.Charset

/**
 * Created by CG on 28.02.2015.
 */


fun OutputStream.write(bb : ByteBuffer) {
    if (bb.hasArray()) {
        this.write(bb.array(), bb.arrayOffset(), bb.remaining())
        bb.position(bb.position() + bb.remaining())
    } else {
        val temp = ByteArray(bb.remaining())
        bb.get(temp)
        this.write(temp)
    }
}

fun httpDateFormat() : DateFormat {
    val sdf = SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
    sdf.setTimeZone(TimeZone.getTimeZone("GMT"))
    return sdf
}

fun String.matcher(p : Pattern) = p.matcher(this)
fun Matcher.findAll() : List<MatchResult> {
    val result = ArrayList<MatchResult>()
    while (find()) {
        result.add(toMatchResult())
    }
    return result
}

fun String.toCharset() = Charset.forName(this)
fun String.unquote() = if ((startsWith("\"") && endsWith("\"") || (startsWith("'") && endsWith("'")))) substring(1, length() - 1) else this