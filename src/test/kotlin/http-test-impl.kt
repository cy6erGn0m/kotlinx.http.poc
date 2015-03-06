package kotlinx.http.test

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import java.net.ServerSocket
import java.io.Reader
import java.util.ArrayList
import java.util.regex.Matcher
import java.io.Writer
import java.util.concurrent.CopyOnWriteArrayList
import java.io.IOException

private fun Matcher.matchGroups() : List<String> =
    if (find()) {
        (0..groupCount()).map { group(it)!! }
    } else emptyList()

private fun String.dropTrailingCarriageReturn() = if (this endsWith "\r") this.substring(0, this.length() - 1) else this

tailRecursive
private fun readLineImpl(r : Reader, sb : StringBuilder, nextChar : Int = r.read()) : StringBuilder = if (nextChar == -1 || nextChar == '\n'.toInt()) sb
    else readLineImpl(r, sb.append(nextChar.toChar()))

private fun Reader.readLine() : String = readLineImpl(this, StringBuilder(1024)).toString()

private fun Throwable?.th() : Unit = if (this != null) throw this

public fun runHttpTest(client : (port : Int) -> Unit, serverBody : (reader : Reader, method : String, path : String, headers : Map<String, List<String>>, Writer) -> Unit) {
    val startLatch = CountDownLatch(1)
    var server : ServerSocket? = null
    val errors = CopyOnWriteArrayList<Throwable>()

    val serverThread = thread {
        try {
            ServerSocket(0).use { ss ->
                server = ss
                startLatch.countDown()

                do {
                    ss.accept().use {
                        val r = it.getInputStream().buffered().reader("ISO-8859-1")
                        val w = it.getOutputStream().buffered().writer("ISO-8859-1")

                        val requestLine = r.readLine()
                        val (method, path, protocol) = "([A-Z]+)\\s+(.*)\\s+(HTTP/[0-9\\.]+)\$".toRegex().matcher(requestLine).matchGroups().drop(1)
                        if (protocol !in listOf("HTTP/1.0", "HTTP/1.1")) {
                            throw IOException("Wrong protocol $protocol")
                        }

                        val lines = ArrayList<String>()
                        do {
                            val line = r.readLine().dropTrailingCarriageReturn()
                            if (line == "") {
                                break
                            }

                            lines.add(line)
                        } while (true)

                        val headers = lines.map {
                            it.split(":").toList().let { pair ->
                                if (pair.size() == 1) pair[0] to ""
                                else if (pair.size() == 2) pair[0] to pair[1]
                                else pair[0] to pair.drop(1).join(":")
                            }
                        }.groupBy { it.first }.mapValues { it.getValue().map { it.second } }

                        serverBody(r, method, path, headers, w)
                    }
                } while (true)
            }
        } catch (any : Throwable) {
            errors.add(any)
        }
    }

    startLatch.await()
    try {
        client(server!!.getLocalPort())
    } catch (any : Throwable) {
        errors.add(any)
    }

    errors.forEach {
        it.printStackTrace()
    }

    try {
        errors.firstOrNull().th()
    } finally {
        try {
            server?.close()
        } catch (ignore : Throwable) {
        }
    }
}
