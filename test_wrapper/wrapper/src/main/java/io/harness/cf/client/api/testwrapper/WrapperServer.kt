package io.harness.cf.client.api.testwrapper

import com.sun.net.httpserver.HttpServer
import io.harness.cf.client.api.Config
import io.harness.cf.client.api.testwrapper.capability.Initialization
import io.harness.cf.client.api.testwrapper.capability.Status
import io.harness.cf.client.api.testwrapper.capability.Termination
import io.harness.cf.client.api.testwrapper.client.WrapperClient
import io.harness.cf.client.api.testwrapper.context.ApiContextFactory
import io.harness.cf.client.api.testwrapper.context.SimpleContextFactory

import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

data class WrapperServer(

    private val port: Int,
    private val apiKey: String,
    private val configuration: Config

) : Initialization, Termination, Status {

    lateinit var client: WrapperClient

    val server: HttpServer = HttpServer.create(

        InetSocketAddress(

            InetAddress.getByName("0.0.0.0"), port
        ), 0
    )

    private val serverContextFactories = listOf(

        SimpleContextFactory(),
        ApiContextFactory()
    )

    override fun init(): Boolean {

        server.executor = Executors.newFixedThreadPool(10)
        server.start()

        serverContextFactories.forEach {

            it.build(this)
        }

        val startTime = System.currentTimeMillis()
        client = WrapperClient(apiKey, configuration)

        while (!client.isInitialized &&
            System.currentTimeMillis() - startTime <= 30_000) {

            Thread.yield()
        }

        return client.isInitialized
    }

    override fun shutdown(): Boolean {

        client.destroy()
        server.stop(0)
        return isNotActive()
    }

    override fun isActive() = client.isInitialized
}
