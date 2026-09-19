#!/usr/bin/env bash
# exchange-simulator 本地基础测试脚本
#
# 用法:
#   ./local-test.sh                     # 全部阶段（条件不足的阶段自动跳过）
#   ./local-test.sh unit boot           # 指定阶段: unit / boot / e2e / all
#   ./local-test.sh --jdk8 /path/jdk8/bin/java boot   # 显式指定 JDK8
#
# 阶段说明:
#   unit : 编译 + 38 个单元测试 + 打包（任意 JDK 可执行）
#   boot : JDK 8 启动 fat jar + HTTP 冒烟（撮合核心依赖 chronicle 仅兼容 JDK 8，
#          自动探测 JDK8：JAVA8_HOME > --jdk8 参数 > JAVA_HOME > 系统常用路径）
#   e2e  : boot 基础上执行 gt-auto SSE 协议回归（需要 PATH 或 ~/go/bin 下有 gt-auto）
#
# 依赖: bash、mvn、curl；boot/e2e 阶段另需 JDK 8，e2e 另需 gt-auto（见 readme）
set -euo pipefail
cd "$(dirname "$0")"

# ---------- 输出工具 ----------
if [[ -t 1 ]]; then
    GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
else
    GREEN=''; YELLOW=''; RED=''; BOLD=''; NC=''
fi
info()    { echo -e "${GREEN}[PASS]${NC} $1"; }
warn()    { echo -e "${YELLOW}[SKIP]${NC} $1"; }
fail()    { echo -e "${RED}[FAIL]${NC} $1"; }
step()    { echo -e "\n${BOLD}==== $1 ====${NC}"; }

APP_PID=""
APP_LOG=""
RESULTS=""
FAILURES=0

record() { # record <phase> <status> <message>
    RESULTS="${RESULTS}$1:$2  "
    if [[ "$2" != "PASS" ]]; then FAILURES=$((FAILURES + 1)); fi
}

cleanup() {
    stop_app
}
trap cleanup EXIT

stop_app() {
    if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
        info "已停止测试应用 (pid=$APP_PID)"
    fi
    APP_PID=""
}

# ---------- 参数解析 ----------
PHASES=()
JAVA8_CMD="${JAVA8_CMD:-}"
while [[ $# -gt 0 ]]; do
    case "$1" in
        unit|boot|e2e|all) PHASES+=("$1"); shift ;;
        --jdk8) JAVA8_CMD="$2"; shift 2 ;;
        -h|--help) grep '^#' "$0" | tail -n +2 | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "未知参数: $1（用法见 ./local-test.sh --help）" >&2; exit 1 ;;
    esac
done
if [[ ${#PHASES[@]} -eq 0 ]]; then PHASES=(all); fi
if [[ "${PHASES[*]}" == *all* ]]; then PHASES=(unit boot e2e); fi

HTTP_PORT=8080
HEALTH_URL="http://localhost:${HTTP_PORT}/api/v1/orders/health"

jar_path() {
    ls target/exchange-simulator-*.jar 2>/dev/null | head -1 || true
}

# ---------- JDK 8 探测 ----------
java8_ok() { # 校验给定 java 可执行文件确实是 1.8
    [[ -x "$1" ]] && "$1" -version 2>&1 | grep -q 'version "1\.8'
}

find_jdk8() {
    if [[ -n "$JAVA8_CMD" ]]; then
        java8_ok "$JAVA8_CMD" && { echo "$JAVA8_CMD"; return 0; }
        return 1
    fi
    if [[ -n "${JAVA8_HOME:-}" ]] && java8_ok "$JAVA8_HOME/bin/java"; then
        echo "$JAVA8_HOME/bin/java"; return 0
    fi
    if [[ -n "${JAVA_HOME:-}" ]] && java8_ok "$JAVA_HOME/bin/java"; then
        echo "$JAVA_HOME/bin/java"; return 0
    fi
    if [[ "$(uname)" == "Darwin" ]] && /usr/libexec/java_home -v 1.8 >/dev/null 2>&1; then
        echo "$(/usr/libexec/java_home -v 1.8)/bin/java"; return 0
    fi
    local candidate
    for candidate in \
        /Library/Java/JavaVirtualMachines/*/Contents/Home \
        /usr/lib/jvm/* /usr/java/* /opt/java/* /opt/zulu* \
        /tmp/zulu8*/Contents/Home
    do
        if java8_ok "$candidate/bin/java"; then
            echo "$candidate/bin/java"; return 0
        fi
    done
    return 1
}

port_busy() { # port_busy <port>: 端口已被监听返回 0
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && { exec 3>&- 3<&-; return 0; } || return 1
}

# ---------- 阶段: unit ----------
run_unit() {
    step "阶段 unit: 编译 + 单元测试 + 打包"
    if ! command -v mvn >/dev/null 2>&1; then
        warn "未找到 mvn，跳过 unit"; record unit SKIP; return 0
    fi
    mvn -B -ntp clean verify
    if [[ -z "$(jar_path)" ]]; then
        fail "mvn verify 通过但未找到 fat jar"; record unit FAIL; return 1
    fi
    info "mvn verify 通过，产物: $(jar_path)"
    record unit PASS
}

# ---------- 应用生命周期 ----------
start_app() { # start_app <java8bin>
    local jar; jar="$(jar_path)"
    if [[ -z "$jar" ]]; then
        fail "未找到 fat jar（先执行 ./local-test.sh unit）"; return 1
    fi
    local port
    for port in 8080 9010 9011; do
        if port_busy "$port"; then
            fail "端口 $port 已被占用（可能有实例在运行），请先停止"; return 1
        fi
    done
    APP_LOG="$(mktemp -t exchange-simulator-smoke)"
    "$1" -jar "$jar" >"$APP_LOG" 2>&1 &
    APP_PID=$!
    local i
    for i in $(seq 1 60); do
        if ! kill -0 "$APP_PID" 2>/dev/null; then
            fail "应用启动进程退出，日志尾部:"; tail -30 "$APP_LOG"; return 1
        fi
        if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
            info "应用启动成功 (pid=$APP_PID, 日志: $APP_LOG)"
            return 0
        fi
        sleep 1
    done
    fail "等待健康检查超时（60s），日志尾部:"; tail -30 "$APP_LOG"
    return 1
}

expect_http() { # expect_http <描述> <期望code> <实际code>
    if [[ "$2" == "$3" ]]; then
        info "$1 (HTTP $3)"
    else
        fail "$1: 期望 HTTP $2, 实际 $3"
        return 1
    fi
}

# ---------- 阶段: boot ----------
run_boot() {
    step "阶段 boot: JDK 8 启动 + HTTP 冒烟"
    local java8
    if ! java8=$(find_jdk8); then
        warn "未找到 JDK 8（可设 JAVA8_HOME 或 --jdk8 指定），跳过 boot"
        record boot SKIP; return 0
    fi
    info "使用 JDK 8: $java8"

    if ! start_app "$java8"; then record boot FAIL; return 1; fi

    local rc=0
    # 1. 健康检查
    expect_http "健康检查" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL")" || rc=1
    # 2. 非法请求应被参数校验拦截（空 orderId / 无 userId / price=0）
    expect_http "参数校验-非法委托返回400" 400 "$(curl -s -o /dev/null -w '%{http_code}' \
        -X POST "http://localhost:${HTTP_PORT}/api/v1/orders/place" \
        -H 'Content-Type: application/json' \
        -d '{"orderId":"","userId":null,"action":"BID","orderType":"GTC","price":0,"size":3,"symbol":10086}')" || rc=1
    # 3. 非数字订单号应返回 400
    expect_http "参数校验-非数字orderId返回400" 400 "$(curl -s -o /dev/null -w '%{http_code}' \
        -X POST "http://localhost:${HTTP_PORT}/api/v1/orders/place" \
        -H 'Content-Type: application/json' \
        -d '{"orderId":"abc","userId":1001,"action":"BID","orderType":"GTC","price":50.0,"size":3,"symbol":10086}')" || rc=1
    # 4. 合法委托且缺省 reservePrice 应回落成功（200/true）
    local body code
    code=$(curl -s -o /tmp/.local-test-body -w '%{http_code}' \
        -X POST "http://localhost:${HTTP_PORT}/api/v1/orders/place" \
        -H 'Content-Type: application/json' \
        -d '{"orderId":"90001","userId":1001,"action":"BID","orderType":"GTC","price":50.0,"size":3,"symbol":10086}')
    body=$(cat /tmp/.local-test-body 2>/dev/null || true)
    if [[ "$code" == "200" && "$body" == "true" ]]; then
        info "合法委托受理成功且 reservePrice 缺省回落 (HTTP 200, body=true)"
    else
        fail "合法委托: 期望 HTTP 200/true, 实际 $code/$body"; rc=1
    fi
    rm -f /tmp/.local-test-body
    # 5. 两个协议端口就绪
    grep -q "SseBinServer started on port :9010" "$APP_LOG" \
        && info "SSE 协议服务就绪 (:9010)" || { fail "SSE 协议服务未就绪"; rc=1; }
    grep -q "SzseBinServer started on port :9011" "$APP_LOG" \
        && info "SZSE 协议服务就绪 (:9011)" || { fail "SZSE 协议服务未就绪"; rc=1; }

    if [[ $rc -eq 0 ]]; then record boot PASS; else record boot FAIL; fi
    stop_app #阶段间释放端口，避免后续阶段冲突
    return $rc
}

# ---------- 阶段: e2e ----------
find_gtauto() {
    if command -v gt-auto >/dev/null 2>&1; then command -v gt-auto; return 0; fi
    local gopath_bin
    gopath_bin="$(go env GOPATH 2>/dev/null || echo "$HOME/go")/bin"
    if [[ -x "$gopath_bin/gt-auto" ]]; then echo "$gopath_bin/gt-auto"; return 0; fi
    if [[ -x "$HOME/go/bin/gt-auto" ]]; then echo "$HOME/go/bin/gt-auto"; return 0; fi
    return 1
}

run_e2e() {
    step "阶段 e2e: gt-auto SSE 协议端到端回归"
    local java8 gtauto
    if ! java8=$(find_jdk8); then
        warn "未找到 JDK 8，跳过 e2e"; record e2e SKIP; return 0
    fi
    if ! gtauto=$(find_gtauto); then
        warn "未找到 gt-auto（安装方式见 readme），跳过 e2e"; record e2e SKIP; return 0
    fi
    info "使用 JDK 8: $java8 / gt-auto: $gtauto"

    if ! start_app "$java8"; then record e2e FAIL; return 1; fi

    local rc=0
    if PATH="$(dirname "$gtauto"):$PATH" bash ./autotest.sh >"$APP_LOG.gtauto" 2>&1; then
        if grep -q '❌' "$APP_LOG.gtauto"; then
            fail "gt-auto 回归存在失败步骤（❌）:"; grep -n '❌' "$APP_LOG.gtauto" | head -10; rc=1
        else
            info "gt-auto 回归全部通过（✅），明细: $APP_LOG.gtauto"
        fi
    else
        fail "gt-auto 执行异常（退出码非 0），日志尾部:"; tail -30 "$APP_LOG.gtauto"; rc=1
    fi

    if [[ $rc -eq 0 ]]; then record e2e PASS; else record e2e FAIL; fi
    stop_app
    return $rc
}

# ---------- 主流程 ----------
for phase in "${PHASES[@]}"; do
    case "$phase" in
        unit) run_unit || true ;;
        boot) run_boot || true ;;
        e2e)  run_e2e  || true ;;
    esac
done

step "结果汇总"
echo -e "${BOLD}${RESULTS}${NC}"
if [[ $FAILURES -gt 0 ]]; then
    fail "共 ${FAILURES} 个阶段未通过"
    exit 1
fi
echo -e "${GREEN}全部执行的阶段通过（被跳过的阶段标记为 SKIP）${NC}"
