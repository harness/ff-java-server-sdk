package io.harness.cf.client.api.testwrapper.context

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.sun.net.httpserver.HttpExchange
import io.harness.cf.client.api.CfClient
import io.harness.cf.client.api.testwrapper.WrapperServer
import io.harness.cf.client.api.testwrapper.context.api.FlagCheckRequest
import io.harness.cf.client.api.testwrapper.context.api.FlagCheckResponse
import io.harness.cf.client.api.testwrapper.context.api.KIND
import io.harness.cf.client.api.testwrapper.context.api.PongResponse
import io.harness.cf.client.api.testwrapper.logging.CfLog
import io.harness.cf.client.api.testwrapper.request.REQUEST_METHOD
import io.harness.cf.client.dto.Target

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.IllegalArgumentException

class ApiContextFactory : CommonContextFactory() {

    private var client: CfClient? = null
    private val defaultTag = "ApiContextFactory"

    private val tag: String = if (ApiContextFactory::class.simpleName != null) {

        ApiContextFactory::class.simpleName
        defaultTag
    } else {

        defaultTag
    }

    companion object {

        const val PATH_PING = "/api/1.0/ping"
        const val PATH_CHECK_FLAG = "/api/1.0/check_flag"
    }

    override fun build(server: WrapperServer) {

        client = server.client
        server.server.createContext(PATH_PING) { exchange -> handleExchange(exchange) }
        server.server.createContext(PATH_CHECK_FLAG) { exchange -> handleExchange(exchange) }
    }

    private fun handleExchange(exchange: HttpExchange) {

        try {

            when (exchange.requestMethod) {

                REQUEST_METHOD.GET -> {

                    when (exchange.requestURI.path) {

                        PATH_PING -> {

                            val pong = PongResponse(true)
                            val json = Gson().toJson(pong)
                            exchange.sendResponseHeaders(200, 0)
                            val output = exchange.responseBody
                            val input = ByteArrayInputStream(json.toByteArray())
                            input.copyTo(output)
                            input.close()
                            output.close()
                        }
                        else -> err404(exchange)
                    }
                }
                REQUEST_METHOD.POST -> {

                    when (exchange.requestURI.path) {

                        PATH_CHECK_FLAG -> {

                            val reader = BufferedReader(exchange.requestBody.reader())

                            val content: String
                            reader.use {

                                content = it.readText()
                                it.close()
                            }

                            val request = Gson().fromJson(content, FlagCheckRequest::class.java)

                            val key = request.flagKey
                            val kind = request.flagKind

                            val target = Target.builder()
                                .name(request.target.targetName)
                                .identifier(request.target.targetIdentifier)
                                .isPrivate(false)
                                .build()

                            CfLog.OUT.v(tag, "key=$key, kind=$kind")

                            if (client == null) {

                                err500(exchange, IllegalStateException("Client is null"))
                            }

                            client?.let {

                                val flagValue: Any = when (kind) {

                                    KIND.BOOLEAN.value -> {

                                        it.boolVariation(key, target, false)
                                    }
                                    KIND.INT.value -> {

                                        it.numberVariation(key, target, 0)
                                    }
                                    KIND.STRING.value -> {

                                        it.stringVariation(key, target, "")
                                    }
                                    KIND.JSON.value -> {

                                        val o = it.jsonVariation(key, target, JsonObject())
                                        Gson().toJson(o)
                                    }
                                    else -> throw IllegalArgumentException("Unknown kind: '$kind'")
                                }

                                CfLog.OUT.v(tag, "Flag value: $flagValue")

                                val checkFlagResponse = FlagCheckResponse(

                                    key,
                                    flagValue.toString()
                                )

                                val json = Gson().toJson(checkFlagResponse)
                                CfLog.OUT.v(tag, "JSON: $json")

                                exchange.sendResponseHeaders(200, 0)
                                val output = exchange.responseBody
                                val input = ByteArrayInputStream(json.toByteArray())
                                input.copyTo(output)
                                input.close()
                                output.close()
                            }
                        }
                        else -> err404(exchange)
                    }
                }
                else -> err404(exchange)
            }
        } catch (e: IllegalArgumentException) {

            err500(exchange, e)
        } catch (e: IOException) {

            err500(exchange, e)
        } catch (e: JsonSyntaxException) {

            err500(exchange, e)
        }
    }
}
