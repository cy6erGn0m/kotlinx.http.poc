package cg.http

import org.junit.Test as test

/**
 * Created by CG on 28.02.2015.
 */

class MainTest {
    test fun main() {
        val request = http.get() withHost "localhost:9090" withPath "/" withRetries 3
        val f = request.send()
    }
}