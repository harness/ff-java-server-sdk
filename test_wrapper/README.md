# Test wrapper module

## Configuration

Make sure that the configuration file is defined (`wrapper.json`):

```json
{
  "selfTest": false,
  "port": 4000,
  "sdkKey": "YOUR_SDK_KEY",
  "logger": "default"
}
```

## Running test wrapper inside a container

Or, to run test wrapper inside the Docker container execute the following sample command:

```
docker build --build-arg PORT=4000; SELF_TEST=false; WRAPPERS_BRANCH=main; \  
SDK_KEY=YOUR_SDK_KEY; HARNESS_JFROG_INT_USR=USERNAME; HARNESS_JFROG_INT_PWD=PASSWORD \  
-t <image_tag> . && \ 
    docker run -p 0.0.0.0:4000:4000 --name android_test_wrapper <image_tag> 
```

Where the following arguments must be provided:

- `PORT` represents the port that will be used
- `SDK_KEY` represents your FF SDK KEY
- `HARNESS_JFROG_INT_USR` represents Harness JFrog username
- `HARNESS_JFROG_INT_PWD` represents Harness JFrog password
- `<image_tag>` Docker image tag name.

Docker image will be created and container started.

## Using test wrapper

Test wrapper will be listening for the API calls on provided port. The following CURL commands
illustrate the use:

- Ping:

```
curl -X GET -H "content-type: application/json" http://localhost:4000/api/1.0/ping
```

Response:

```json
{"pong":true}
```

- Feature flag check:

```
curl -X POST -d '{"flag_kind": "boolean", "flag_key": "flag1", "target": {"target_identifier": "test", "target_name": "test"}}' \
 -H "content-type: application/json" http://localhost:4000/api/1.0/check_flag
```

Response:

```json
{"flag_key":"flag1","flag_value":"true"}
```
