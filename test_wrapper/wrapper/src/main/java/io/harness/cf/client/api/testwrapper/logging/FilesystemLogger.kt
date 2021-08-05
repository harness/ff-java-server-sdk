package io.harness.cf.client.api.testwrapper.logging

import net.milosvasic.logger.FilesystemLogger

class FilesystemLogger : WrapperLogging {

    private val logger = FilesystemLogger()

    init {

        logger.setStructured(false)
    }

    override fun v(tag: String, message: String) {

        logger.v(tag, message)
    }

    override fun v(tag: String, message: String, throwable: Throwable) {

        logger.w(tag, message)
        logger.w(tag, Exception(throwable))
    }

    override fun d(tag: String, message: String) {

        logger.d(tag, message)
    }

    override fun d(tag: String, message: String, throwable: Throwable) {

        logger.e(tag, message)
        logger.e(tag, Exception(throwable))
    }

    override fun i(tag: String, message: String) {

        logger.i(tag, message)
    }

    override fun i(tag: String, message: String, throwable: Throwable) {

        logger.e(tag, message)
        logger.e(tag, Exception(throwable))
    }

    override fun w(tag: String, message: String) {

        logger.w(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable) {

        logger.w(tag, message)
        logger.w(tag, Exception(throwable))
    }

    override fun w(tag: String, throwable: Throwable) {

        logger.w(tag, Exception(throwable))
    }

    override fun e(tag: String, message: String) {

        logger.e(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable) {

        logger.e(tag, message)
        logger.e(tag, Exception(throwable))
    }

    override fun wtf(tag: String, message: String) {

        logger.e(tag, message)
    }

    override fun wtf(tag: String, message: String, throwable: Throwable) {

        logger.e(tag, message)
        logger.e(tag, Exception(throwable))
    }

    override fun wtf(tag: String, throwable: Throwable) {

        logger.e(tag, Exception(throwable))
    }
}
