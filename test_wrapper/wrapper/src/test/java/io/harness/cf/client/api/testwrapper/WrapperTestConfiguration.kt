package io.harness.cf.client.api.testwrapper

import io.harness.cf.client.api.testwrapper.logging.LoggerType

data class WrapperTestConfiguration(

    val selfTest: Boolean = true,
    val port: Int = 4000,
    val sdkKey: String,
    val logger: String = LoggerType.DEFAULT.type
) {

    companion object {

        const val CONFIGURATION_FILE = "wrapper.json"
    }
}
