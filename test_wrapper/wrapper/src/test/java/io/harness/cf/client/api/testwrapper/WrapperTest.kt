package io.harness.cf.client.api.testwrapper

import org.junit.Test

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.harness.cf.client.api.Config
import io.harness.cf.client.api.testwrapper.logging.CfLog
import io.harness.cf.client.api.testwrapper.logging.FilesystemLogger
import io.harness.cf.client.api.testwrapper.logging.LoggerType

import org.junit.Assert
import org.junit.Before

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.harness.cf.client.api.testwrapper.context.api.*
import io.harness.cf.client.api.testwrapper.context.api.Target

class WrapperTest {

    /**
     * Start the wrapper server and execute the tests.
     *
     * True == Start the local server, execute tests and shutdown the server.
     * False == Start the local server and wait for 3rd party to perform the tests.
     */
    private var selfTest = true

    /**
     * Port to be used by local server instance.
     */
    private var serverPort = 4000

    /**
     * API key used to initialize the SDK.
     */
    private var sdkKey = "YOUR_SDK_KEY"

    /**
     * Will we write logs to the log ile or to the system console?
     */
    private var filesystemLogger = false

    private lateinit var server: WrapperServer

    private val defaultTag = "WrapperTest"

    private val tag = if (WrapperTest::class.simpleName != null) {

        WrapperTest::class.simpleName
        defaultTag
    } else {

        defaultTag
    }

    @Before
    fun setup() {

        var inputStream: InputStream? = null
        try {

            inputStream = File(WrapperTestConfiguration.CONFIGURATION_FILE).inputStream()
            val inputString = inputStream.bufferedReader().use { it.readText() }
            val config = Gson().fromJson(inputString, WrapperTestConfiguration::class.java)

            selfTest = config.selfTest
            serverPort = config.port
            sdkKey = config.sdkKey

            val loggerType = config.logger
            filesystemLogger = loggerType == LoggerType.FILESYSTEM.type

            if (filesystemLogger) {

                CfLog.customMode(FilesystemLogger())
            }

            CfLog.OUT.v(tag, "$config")

        } catch (e: NullPointerException) {

            Assert.fail(e.message)
        } catch (e: SecurityException) {

            Assert.fail(e.message)
        } catch (e: JsonSyntaxException) {

            Assert.fail(e.message)
        } catch (e: FileNotFoundException) {

            CfLog.OUT.v(tag, "No test configuration file provided")
        } finally {

            inputStream?.let {

                try {

                    it.close()
                } catch (e: IOException) {

                    CfLog.OUT.w(tag, e)
                }
            }
        }

        val configuration = Config.builder()
            .analyticsEnabled(true)
            .streamEnabled(true)
            .pollIntervalInSeconds(60)
            .build()

        server = WrapperServer(

            port = serverPort,
            sdkKey = sdkKey,
            configuration = configuration
        )
    }

    @Test
    fun testSDK() {

        Assert.assertTrue(

            initLocalServer()
        )

        CfLog.OUT.v(tag, "Local server is running")

        if (selfTest) {

            Assert.assertTrue(

                runTests()
            )

            CfLog.OUT.v(tag, "Test have been executed")

            Assert.assertTrue(

                terminateLocalServer()
            )

            CfLog.OUT.v(tag, "Local server has been shut down")
        } else {

            while (server.isActive()) {

                Thread.yield()
            }
        }
    }

    private fun initLocalServer(): Boolean {

        CfLog.OUT.v(tag, "Initializing local server")
        return server.init()
    }

    private fun runTests(): Boolean {

        CfLog.OUT.v(tag, "Running tests")

        val calls = mutableListOf<Call<*>>()
        val flagChecks = mutableMapOf<KIND, Call<FlagCheckResponse>>()

        val gsonBuilder = GsonBuilder()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:$serverPort/")
            .addConverterFactory(GsonConverterFactory.create(gsonBuilder.create()))
            .build()

        val apiContextService = retrofit.create(ApiContextService::class.java)
        val simpleContextService = retrofit.create(SimpleContextService::class.java)

        val flagCheckRequests = mapOf(

            KIND.BOOLEAN to "flag1",
            KIND.INT to "flag2",
            KIND.STRING to "flag3",
            KIND.JSON to "flag4"
        )

        calls.addAll(

            listOf(

                apiContextService.ping(),
                simpleContextService.version()
            )
        )

        flagCheckRequests.forEach { (key, value) ->

            val target = Target("test", "test")
            val request = FlagCheckRequest(key.value, value, target)

            flagChecks[key] = apiContextService.checkFlag(request)
        }

        calls.forEach { request ->

            val response = request.execute()
            val msg = getMsg(request, response)

            if (!response.isSuccessful) {

                CfLog.OUT.e(tag, getErrMsg(msg, response))
                return false
            }

            CfLog.OUT.i(tag, msg)
        }

        flagChecks.forEach { (key, request) ->

            val response = request.execute()
            val msg = getMsg(request, response)

            if (!response.isSuccessful) {

                CfLog.OUT.e(tag, getErrMsg(msg, response))
                return false
            }

            CfLog.OUT.i(tag, msg)

            when (key) {

                KIND.BOOLEAN -> {

                    val value = response.body()
                    Assert.assertNotNull(value)

                    value?.let { v ->

                        val b = v.flagValue.toBoolean()
                        Assert.assertTrue(b)
                    }
                }
                KIND.INT -> {

                    val value = response.body()
                    Assert.assertNotNull(value)

                    value?.let { v ->

                        val no = v.flagValue.toDouble()
                        Assert.assertTrue(no > 0)
                    }
                }
                KIND.STRING -> {

                    val value = response.body()
                    Assert.assertNotNull(value)

                    value?.let { v ->

                        val s = v.flagValue
                        Assert.assertNotNull(s)
                        Assert.assertFalse(s.isEmpty())
                        Assert.assertFalse(s.isBlank())
                    }
                }
                KIND.JSON -> {

                    val value = response.body()
                    Assert.assertNotNull(value)

                    value?.let { v ->

                        val j = v.flagValue
                        Assert.assertNotNull(j)
                        Assert.assertFalse(j.isEmpty())
                        Assert.assertFalse(j.isBlank())

                        val o = Gson().fromJson(j, JsonObject::class.java)
                        Assert.assertNotNull(o)
                    }
                }
                else -> Assert.fail("Unknown kind: '$key'")
            }
        }

        return true
    }

    private fun terminateLocalServer(): Boolean {

        CfLog.OUT.v(tag, "Shutting down local server")
        return server.shutdown()
    }

    private fun getErrMsg(

        msg: String,
        response: Response<out Any>

    ) = "$msg, error=\"${response.errorBody()?.string()}\""

    private fun getMsg(

        it: Call<*>,
        response: Response<out Any>

    ) = "url=${it.request().url} code=${response.code()}, payload=${response.body()}"
}
