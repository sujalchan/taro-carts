#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

browser_places_api_key="${GOOGLE_MAPS_BROWSER_API_KEY:-}"
customer_provisioning_secret="${LOGIN_CUSTOMER_PROVISIONING_SECRET:-}"
dispatcher_provisioning_secret="${LOGIN_DISPATCHER_PROVISIONING_SECRET:-}"
secret_names=(
    LOGIN_CUSTOMER_VALIDATION_SECRET LOGIN_DISPATCHER_VALIDATION_SECRET
    LOGIN_ALLOCATION_VALIDATION_SECRET LOGIN_TARO_VALIDATION_SECRET
    ALLOCATION_CUSTOMER_REFERENCE_SECRET ALLOCATION_DISPATCHER_REFERENCE_SECRET
    ALLOCATION_TARO_REFERENCE_SECRET
    ALLOCATION_CUSTOMER_ASSERTION_SECRET ALLOCATION_DISPATCHER_ASSERTION_SECRET
)
declare -A service_secrets
for secret_name in "${secret_names[@]}"; do
    service_secrets[$secret_name]="${!secret_name:-}"
done
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
        for secret_name in "${secret_names[@]}"; do
            if [[ -z "${service_secrets[$secret_name]}" && "$line" == "$secret_name="* ]]; then
                service_secrets[$secret_name]="${line#*=}"
                service_secrets[$secret_name]="${service_secrets[$secret_name]%$'\r'}"
            fi
        done
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
all_secrets=("$customer_provisioning_secret" "$dispatcher_provisioning_secret")
for secret_name in "${secret_names[@]}"; do
    if [[ -z "${service_secrets[$secret_name]}" ]]; then
        service_secrets[$secret_name]="$(openssl rand -hex 32)"
    fi
    if [[ ${#service_secrets[$secret_name]} -lt 32 ]]; then
        printf '%s must contain at least 32 characters.\n' "$secret_name" >&2
        exit 1
    fi
    for existing_secret in "${all_secrets[@]}"; do
        if [[ "${service_secrets[$secret_name]}" == "$existing_secret" ]]; then
            printf 'All internal service secrets must be different.\n' >&2
            exit 1
        fi
    done
    all_secrets+=("${service_secrets[$secret_name]}")
done
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
        for secret_name in "${secret_names[@]}"; do
            unset "$secret_name"
        done
        if [[ "$directory" == "login-service" || "$directory" == "customer-service" ]]; then
            export LOGIN_CUSTOMER_PROVISIONING_SECRET="$customer_provisioning_secret"
        fi
        if [[ "$directory" == "login-service" || "$directory" == "dispatcher-service" ]]; then
            export LOGIN_DISPATCHER_PROVISIONING_SECRET="$dispatcher_provisioning_secret"
        fi
        if [[ "$directory" == "login-service" ]]; then
            for secret_name in LOGIN_CUSTOMER_VALIDATION_SECRET LOGIN_DISPATCHER_VALIDATION_SECRET LOGIN_ALLOCATION_VALIDATION_SECRET LOGIN_TARO_VALIDATION_SECRET; do
                export "$secret_name=${service_secrets[$secret_name]}"
            done
        fi
        if [[ "$directory" == "customer-service" ]]; then
            export LOGIN_CUSTOMER_VALIDATION_SECRET="${service_secrets[LOGIN_CUSTOMER_VALIDATION_SECRET]}"
            export ALLOCATION_CUSTOMER_REFERENCE_SECRET="${service_secrets[ALLOCATION_CUSTOMER_REFERENCE_SECRET]}"
            export ALLOCATION_CUSTOMER_ASSERTION_SECRET="${service_secrets[ALLOCATION_CUSTOMER_ASSERTION_SECRET]}"
        fi
        if [[ "$directory" == "dispatcher-service" ]]; then
            export LOGIN_DISPATCHER_VALIDATION_SECRET="${service_secrets[LOGIN_DISPATCHER_VALIDATION_SECRET]}"
            export ALLOCATION_DISPATCHER_REFERENCE_SECRET="${service_secrets[ALLOCATION_DISPATCHER_REFERENCE_SECRET]}"
            export ALLOCATION_DISPATCHER_ASSERTION_SECRET="${service_secrets[ALLOCATION_DISPATCHER_ASSERTION_SECRET]}"
        fi
        if [[ "$directory" == "allocation-service" ]]; then
            export LOGIN_ALLOCATION_VALIDATION_SECRET="${service_secrets[LOGIN_ALLOCATION_VALIDATION_SECRET]}"
            export ALLOCATION_CUSTOMER_REFERENCE_SECRET="${service_secrets[ALLOCATION_CUSTOMER_REFERENCE_SECRET]}"
            export ALLOCATION_DISPATCHER_REFERENCE_SECRET="${service_secrets[ALLOCATION_DISPATCHER_REFERENCE_SECRET]}"
            export ALLOCATION_TARO_REFERENCE_SECRET="${service_secrets[ALLOCATION_TARO_REFERENCE_SECRET]}"
            export ALLOCATION_CUSTOMER_ASSERTION_SECRET="${service_secrets[ALLOCATION_CUSTOMER_ASSERTION_SECRET]}"
            export ALLOCATION_DISPATCHER_ASSERTION_SECRET="${service_secrets[ALLOCATION_DISPATCHER_ASSERTION_SECRET]}"
        fi
        if [[ "$directory" == "taro-service" ]]; then
            export LOGIN_TARO_VALIDATION_SECRET="${service_secrets[LOGIN_TARO_VALIDATION_SECRET]}"
            export ALLOCATION_TARO_REFERENCE_SECRET="${service_secrets[ALLOCATION_TARO_REFERENCE_SECRET]}"
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
