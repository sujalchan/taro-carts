#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

browser_places_api_key="${GOOGLE_MAPS_BROWSER_API_KEY:-}"
customer_provisioning_secret="${LOGIN_CUSTOMER_PROVISIONING_SECRET:-}"
dispatcher_provisioning_secret="${LOGIN_DISPATCHER_PROVISIONING_SECRET:-}"
if [[ -f "$ROOT_DIR/.env" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ -z "$browser_places_api_key" && "$line" == GOOGLE_MAPS_BROWSER_API_KEY=* ]]; then
            browser_places_api_key="${line#*=}"
            browser_places_api_key="${browser_places_api_key%$'\r'}"
        fi
        if [[ -z "$customer_provisioning_secret" && "$line" == LOGIN_CUSTOMER_PROVISIONING_SECRET=* ]]; then
            customer_provisioning_secret="${line#*=}"
            customer_provisioning_secret="${customer_provisioning_secret%$'\r'}"
        fi
        if [[ -z "$dispatcher_provisioning_secret" && "$line" == LOGIN_DISPATCHER_PROVISIONING_SECRET=* ]]; then
            dispatcher_provisioning_secret="${line#*=}"
            dispatcher_provisioning_secret="${dispatcher_provisioning_secret%$'\r'}"
        fi
    done < "$ROOT_DIR/.env"
fi
if [[ -z "$customer_provisioning_secret" ]]; then
    customer_provisioning_secret="$(openssl rand -hex 32)"
fi
if [[ -z "$dispatcher_provisioning_secret" ]]; then
    dispatcher_provisioning_secret="$(openssl rand -hex 32)"
fi
if [[ ${#customer_provisioning_secret} -lt 32 || ${#dispatcher_provisioning_secret} -lt 32 ]]; then
    printf 'Provisioning secrets must contain at least 32 characters.\n' >&2
    exit 1
fi
if [[ "$customer_provisioning_secret" == "$dispatcher_provisioning_secret" ]]; then
    printf 'Customer and dispatcher provisioning secrets must be different.\n' >&2
    exit 1
fi
unset GOOGLE_MAPS_BROWSER_API_KEY

service_pids=()

start_service() {
    local name="$1"
    local directory="$2"
    local pid

    (
        cd "$ROOT_DIR/$directory"
        if [[ "$directory" == "customer-service" || "$directory" == "dispatcher-service" || "$directory" == "login-service" ]]; then
            if [[ -n "$browser_places_api_key" ]]; then
                export GOOGLE_MAPS_BROWSER_API_KEY="$browser_places_api_key"
            fi
        fi
        unset LOGIN_CUSTOMER_PROVISIONING_SECRET LOGIN_DISPATCHER_PROVISIONING_SECRET
        if [[ "$directory" == "login-service" || "$directory" == "customer-service" ]]; then
            export LOGIN_CUSTOMER_PROVISIONING_SECRET="$customer_provisioning_secret"
        fi
        if [[ "$directory" == "login-service" || "$directory" == "dispatcher-service" ]]; then
            export LOGIN_DISPATCHER_PROVISIONING_SECRET="$dispatcher_provisioning_secret"
        fi
        exec ./mvnw spring-boot:run
    ) >"$LOG_DIR/$name.log" 2>&1 &
    pid=$!
    service_pids+=("$pid")
    printf 'Started %s (PID %s); log: %s/%s.log\n' "$name" "$pid" "$LOG_DIR" "$name"
}

stop_services() {
    trap - INT TERM EXIT
    for pid in "${service_pids[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    for pid in "${service_pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    printf '\nStopped backend services.\n'
}

trap stop_services INT TERM EXIT

start_service login-service login-service
start_service customer-service customer-service
start_service dispatcher-service dispatcher-service
start_service taro-service taro-service
start_service allocation-service allocation-service

printf '\nServices are starting. Press Ctrl+C to stop all of them.\n'
printf 'Login:       http://localhost:8085/login\nCustomer:    http://localhost:8081\nDispatcher:  http://localhost:8084/dispatchers\nTaro:        http://localhost:8083\nAllocations: http://localhost:8082\n\n'
printf 'Streaming service logs below.\n'
tail -n 20 -f "$LOG_DIR/login-service.log" "$LOG_DIR/customer-service.log" "$LOG_DIR/dispatcher-service.log" "$LOG_DIR/taro-service.log" "$LOG_DIR/allocation-service.log" &
tail_pid=$!
service_pids+=("$tail_pid")

wait "$tail_pid"
