#!/bin/sh

curl -X POST -d '{"flag_kind": "boolean", "flag_key": "flag1", "target": {"target_identifier": "test", "target_name": "test"}}' \
 -H "content-type: application/json" http://localhost:4000/api/1.0/check_flag
