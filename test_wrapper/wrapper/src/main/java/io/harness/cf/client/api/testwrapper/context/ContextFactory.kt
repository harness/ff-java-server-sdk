package io.harness.cf.client.api.testwrapper.context

import io.harness.cf.client.api.testwrapper.WrapperServer

internal interface ContextFactory {

    fun build(server: WrapperServer)
}
