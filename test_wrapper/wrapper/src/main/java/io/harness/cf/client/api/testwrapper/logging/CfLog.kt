package io.harness.cf.client.api.testwrapper.logging

class CfLog {

    companion object {

        var OUT: WrapperLogging = ConsoleLogger()

        fun customMode(logging: WrapperLogging) {

            OUT = logging
        }
    }
}
