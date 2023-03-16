## Flag Cleanup (beta)
The java sdk supports automated code cleanup using the Flag Cleanup drone plugin. See [here](https://github.com/harness/flag_cleanup) for detailed docs on usage of the plugin.

### Run example
1. View the [example file](/examples/src/main/java/io/harness/ff/code_cleanup_examples/SampleClass.java) and observe the if else block using our feature flag ```STALE_FLAG```
2. Run the flag cleanup plugin from this directory. This can be done by running the following docker container

```docker run -v ${PWD}:/java-sdk -e PLUGIN_DEBUG=true -e PLUGIN_PATH_TO_CODEBASE="/java-sdk" -e PLUGIN_PATH_TO_CONFIGURATIONS="/java-sdk/config" -e PLUGIN_LANGUAGE="java" -e PLUGIN_SUBSTITUTIONS="stale_flag_name=STALE_FLAG,treated=true,treated_complement=false" harness/flag_cleanup:latest```

3. Observe that the `if else` block for `STALE_FLAG` has been removed from the code and the flag is now treated as globally true.


### Further reading
For more rules.toml examples see the test cases [here](https://github.com/uber/piranha/tree/master/test-resources/java)