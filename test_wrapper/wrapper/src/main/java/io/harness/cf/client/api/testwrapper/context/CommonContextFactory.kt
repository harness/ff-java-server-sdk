package io.harness.cf.client.api.testwrapper.context

import com.sun.net.httpserver.HttpExchange
import java.io.ByteArrayInputStream
import java.lang.Exception

abstract class CommonContextFactory : ContextFactory {

    protected fun err404(exchange: HttpExchange) {

        exchange.sendResponseHeaders(404, 0)
        val output = exchange.responseBody
        val input = ByteArrayInputStream("Not found".toByteArray())
        input.copyTo(output)
        input.close()
        output.close()
    }

    protected fun err500(exchange: HttpExchange, e: Exception) {

        exchange.sendResponseHeaders(500, 0)
        val output = exchange.responseBody
        val input = ByteArrayInputStream(e.message?.toByteArray())
        input.copyTo(output)
        input.close()
        output.close()
    }
}
