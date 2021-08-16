package io.harness.cf.client.api.testwrapper.context.api

import io.harness.cf.client.api.testwrapper.context.SimpleContextFactory
import retrofit2.Call
import retrofit2.http.GET

interface SimpleContextService {

    @GET(SimpleContextFactory.PATH_VERSION)
    fun version(): Call<VersionResponse>
}
