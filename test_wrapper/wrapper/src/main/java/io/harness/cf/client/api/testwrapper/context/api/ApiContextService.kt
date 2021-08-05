package io.harness.cf.client.api.testwrapper.context.api

import io.harness.cf.client.api.testwrapper.context.ApiContextFactory
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiContextService {

    @GET(ApiContextFactory.PATH_PING)
    fun ping(): Call<PongResponse>

    @POST(ApiContextFactory.PATH_CHECK_FLAG)
    fun checkFlag(@Body payload: FlagCheckRequest): Call<FlagCheckResponse>
}
