package io.harness.cf.client.api.testwrapper.logging

class CfLog {

    companion object {

        var OUT: WrapperLogging = FilesystemLogger()

        fun customMode(logging: WrapperLogging) {

            OUT = logging
        }
    }
}
