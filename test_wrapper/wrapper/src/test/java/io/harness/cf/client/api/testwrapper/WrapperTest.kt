package io.harness.cf.client.api.testwrapper

import org.junit.Test

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
    private var apiKey = "YOUR_API_KEY"

    /**
     * Will we write logs to the log ile or to the system console?
     */
    private var filesystemLogger = false

    @Test
    fun testSDK() {

        // TODO:
    }
}
