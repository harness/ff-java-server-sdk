package io.harness.cf.client.api.testwrapper.context.api

import com.google.gson.annotations.SerializedName

data class FlagCheckRequest(

    @SerializedName("flag_kind")
    val flagKind: String,

    @SerializedName("flag_key")
    val flagKey: String,

    val target: Target
)
