#!/usr/bin/env bash
# gt-auto 端到端协议回归：SSE (:9010) + SZSE (:9011)
# 需先启动 exchange-simulator（本地可用 ./local-test.sh boot/e2e，或 java -jar target/*.jar）
set -uo pipefail
cd "$(dirname "$0")"

FAILED=0

run_suite() { # run_suite <名称> <toml> <caseCsv>
    local name="$1" toml="$2" cases="$3"
    echo "==== gt-auto 回归: $name ===="
    if ! gt-auto --config "$toml" --casePath "$cases"; then
        echo "!!!! $name 回归失败"
        FAILED=1
    fi
}

run_suite "SSE" testcase/sse/gw-auto-sse.toml testcase/sse/sse_test_case.csv
run_suite "SZSE" testcase/szse/gw-auto-szse.toml testcase/szse/szse_test_case.csv

exit $FAILED
