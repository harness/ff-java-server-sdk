package io.harness.cf.client.api.testwrapper.client

import io.harness.cf.client.api.CfClient
import io.harness.cf.client.api.Config

class WrapperClient(sdkKey: String, config: Config) : CfClient(sdkKey, config)
