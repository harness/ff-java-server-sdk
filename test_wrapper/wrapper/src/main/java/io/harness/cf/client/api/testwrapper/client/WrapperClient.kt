package io.harness.cf.client.api.testwrapper.client

import io.harness.cf.client.api.CfClient
import io.harness.cf.client.api.Config

class WrapperClient(apiKey: String, config: Config) : CfClient(apiKey, config)
