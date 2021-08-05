package io.harness.cf.client.api.testwrapper.logging

interface WrapperLogging {

    /**
     * Log verbose.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun v(tag: String, message: String)

    /**
     * Log debug.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun d(tag: String, message: String)

    /**
     * Log information.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun i(tag: String, message: String)

    /**
     * Log warning.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun w(tag: String, message: String)

    /**
     * Log error.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun e(tag: String, message: String)

    /**
     * Log verbose.
     * @param tag Log tag.
     * @param message Log message.
     * @param throwable Throwable containing stacktrace data.
     */
    fun v(tag: String, message: String, throwable: Throwable)

    /**
     * Log debug.
     * @param tag Log tag.
     * @param message Log message.
     * @param throwable Throwable containing stacktrace data.
     */
    fun d(tag: String, message: String, throwable: Throwable)

    /**
     * Log info.
     * @param tag Log tag.
     * @param message Log message.
     * @param throwable Throwable containing stacktrace data.
     */
    fun i(tag: String, message: String, throwable: Throwable)

    /**
     * Log warning.
     * @param tag Log tag.
     * @param message Log message.
     * @param throwable Throwable containing stacktrace data.
     */
    fun w(tag: String, message: String, throwable: Throwable)

    /**
     * Log error.
     * @param tag Log tag.
     * @param message Log message.
     * @param throwable Throwable containing stacktrace data.
     */
    fun e(tag: String, message: String, throwable: Throwable)

    /**
     * Log warning.
     * @param tag Log tag.
     * @param throwable Throwable containing stacktrace data.
     */
    fun w(tag: String, throwable: Throwable)

    /**
     * Log assertion.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun wtf(tag: String, message: String)

    /**
     * Log assertion.
     * @param tag Log tag.
     * @param message Log message.
     * @param throwable Throwable containing stacktrace data.
     */
    fun wtf(tag: String, message: String, throwable: Throwable)

    /**
     * Log assertion.
     * @param tag Log tag.
     * @param throwable Throwable containing stacktrace data.
     */
    fun wtf(tag: String, throwable: Throwable)
}
