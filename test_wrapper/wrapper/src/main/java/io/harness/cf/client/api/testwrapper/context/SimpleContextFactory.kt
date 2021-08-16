package io.harness.cf.client.api.testwrapper.context

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.harness.cf.Version
import io.harness.cf.client.api.testwrapper.WrapperServer
import io.harness.cf.client.api.testwrapper.context.api.VersionResponse
import io.harness.cf.client.api.testwrapper.request.REQUEST_METHOD
import java.io.ByteArrayInputStream
import java.io.IOException

internal class SimpleContextFactory : CommonContextFactory() {

    companion object {

        const val PATH_VERSION = "/sdk/version"
    }

    override fun build(server: WrapperServer) {

        server.server.createContext(PATH_VERSION) { exchange -> handleExchange(exchange) }
    }

    private fun handleExchange(exchange: HttpExchange) {

        try {

            when (exchange.requestMethod) {

                REQUEST_METHOD.GET -> {

                    when (exchange.requestURI.path) {

                        PATH_VERSION -> {

                            val version = VersionResponse(Version.VERSION)
                            val json = Gson().toJson(version)
                            exchange.sendResponseHeaders(200, 0)
                            val output = exchange.responseBody
                            val input = ByteArrayInputStream(json.toByteArray())
                            input.copyTo(output)
                            input.close()
                            output.close()
                        }
                        else -> {

                            err404(exchange)
                        }
                    }
                }
            }
        } catch (e: IOException) {

            err500(exchange, e)
        }
    }
}
