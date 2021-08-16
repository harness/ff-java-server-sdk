package io.harness.cf.client.api.testwrapper.capability

interface Status {

    fun isActive(): Boolean

    fun isNotActive() = !isActive()
}
