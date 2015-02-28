package cg.http.util

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Created by CG on 28.02.2015.
 */

fun <T> failedFuture(e : Throwable) = object : Future<T> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
    override fun isCancelled(): Boolean = false
    override fun isDone(): Boolean = true

    override fun get(): T = throw ExecutionException(e)
    override fun get(timeout: Long, unit: TimeUnit): T = throw ExecutionException(e)
}

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