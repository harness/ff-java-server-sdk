package io.harness.cf.client.api.testwrapper.context.api

import com.google.gson.annotations.SerializedName

data class Target(

    @SerializedName("target_identifier")
    val targetIdentifier: String,

    @SerializedName("target_name")
    val targetName: String
)
