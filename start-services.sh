#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

browser_places_api_key="${GOOGLE_MAPS_BROWSER_API_KEY:-}"
server_maps_api_key="${GOOGLE_MAPS_SERVER_API_KEY:-}"
login_service_url="${LOGIN_SERVICE_URL:-http://localhost:8085}"
login_session_lifetime="${LOGIN_SESSION_LIFETIME:-PT8H}"
login_cookie_secure="${LOGIN_COOKIE_SECURE:-false}"
customer_provisioning_secret="${LOGIN_CUSTOMER_PROVISIONING_SECRET:-}"
dispatcher_provisioning_secret="${LOGIN_DISPATCHER_PROVISIONING_SECRET:-}"
secret_names=(
    LOGIN_CUSTOMER_VALIDATION_SECRET LOGIN_DISPATCHER_VALIDATION_SECRET
    LOGIN_ALLOCATION_VALIDATION_SECRET LOGIN_TARO_VALIDATION_SECRET
    ALLOCATION_CUSTOMER_REFERENCE_SECRET ALLOCATION_DISPATCHER_REFERENCE_SECRET
    ALLOCATION_TARO_REFERENCE_SECRET
    ALLOCATION_CUSTOMER_ASSERTION_SECRET ALLOCATION_DISPATCHER_ASSERTION_SECRET
)
service_secret_values=()
for secret_name in "${secret_names[@]}"; do
    service_secret_values+=("${!secret_name:-}")
done

secret_index() {
    local target="$1"
    local index
    for ((index = 0; index < ${#secret_names[@]}; index++)); do
        if [[ "${secret_names[$index]}" == "$target" ]]; then
            printf '%s' "$index"
            return 0
        fi
    done
    return 1
}

customer_validation_index="$(secret_index LOGIN_CUSTOMER_VALIDATION_SECRET)"
dispatcher_validation_index="$(secret_index LOGIN_DISPATCHER_VALIDATION_SECRET)"
allocation_validation_index="$(secret_index LOGIN_ALLOCATION_VALIDATION_SECRET)"
taro_validation_index="$(secret_index LOGIN_TARO_VALIDATION_SECRET)"
customer_reference_index="$(secret_index ALLOCATION_CUSTOMER_REFERENCE_SECRET)"
dispatcher_reference_index="$(secret_index ALLOCATION_DISPATCHER_REFERENCE_SECRET)"
taro_reference_index="$(secret_index ALLOCATION_TARO_REFERENCE_SECRET)"
customer_assertion_index="$(secret_index ALLOCATION_CUSTOMER_ASSERTION_SECRET)"
dispatcher_assertion_index="$(secret_index ALLOCATION_DISPATCHER_ASSERTION_SECRET)"
if [[ -f "$ROOT_DIR/.env" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ -z "$browser_places_api_key" && "$line" == GOOGLE_MAPS_BROWSER_API_KEY=* ]]; then
            browser_places_api_key="${line#*=}"
            browser_places_api_key="${browser_places_api_key%$'\r'}"
        fi
        if [[ -z "$server_maps_api_key" && "$line" == GOOGLE_MAPS_SERVER_API_KEY=* ]]; then
            server_maps_api_key="${line#*=}"
            server_maps_api_key="${server_maps_api_key%$'\r'}"
        fi
        if [[ "${LOGIN_SERVICE_URL+x}" != x && "$line" == LOGIN_SERVICE_URL=* ]]; then
            login_service_url="${line#*=}"
            login_service_url="${login_service_url%$'\r'}"
        fi
        if [[ "${LOGIN_SESSION_LIFETIME+x}" != x && "$line" == LOGIN_SESSION_LIFETIME=* ]]; then
            login_session_lifetime="${line#*=}"
            login_session_lifetime="${login_session_lifetime%$'\r'}"
        fi
        if [[ "${LOGIN_COOKIE_SECURE+x}" != x && "$line" == LOGIN_COOKIE_SECURE=* ]]; then
            login_cookie_secure="${line#*=}"
            login_cookie_secure="${login_cookie_secure%$'\r'}"
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
            secret_index_value="$(secret_index "$secret_name")"
            if [[ -z "${service_secret_values[$secret_index_value]}" && "$line" == "$secret_name="* ]]; then
                service_secret_values[$secret_index_value]="${line#*=}"
                service_secret_values[$secret_index_value]="${service_secret_values[$secret_index_value]%$'\r'}"
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
for secret_index_value in "${!secret_names[@]}"; do
    secret_name="${secret_names[$secret_index_value]}"
    if [[ -z "${service_secret_values[$secret_index_value]}" ]]; then
        service_secret_values[$secret_index_value]="$(openssl rand -hex 32)"
    fi
    if [[ ${#service_secret_values[$secret_index_value]} -lt 32 ]]; then
        printf '%s must contain at least 32 characters.\n' "$secret_name" >&2
        exit 1
    fi
    for existing_secret in "${all_secrets[@]}"; do
        if [[ "${service_secret_values[$secret_index_value]}" == "$existing_secret" ]]; then
            printf 'All internal service secrets must be different.\n' >&2
            exit 1
        fi
    done
    all_secrets+=("${service_secret_values[$secret_index_value]}")
done
unset GOOGLE_MAPS_BROWSER_API_KEY
unset GOOGLE_MAPS_SERVER_API_KEY
export LOGIN_SERVICE_URL="$login_service_url"
export LOGIN_SESSION_LIFETIME="$login_session_lifetime"
export LOGIN_COOKIE_SECURE="$login_cookie_secure"

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
            if [[ -n "$server_maps_api_key" ]]; then
                export GOOGLE_MAPS_SERVER_API_KEY="$server_maps_api_key"
            fi
            export LOGIN_CUSTOMER_VALIDATION_SECRET="${service_secret_values[$customer_validation_index]}"
            export LOGIN_DISPATCHER_VALIDATION_SECRET="${service_secret_values[$dispatcher_validation_index]}"
            export LOGIN_ALLOCATION_VALIDATION_SECRET="${service_secret_values[$allocation_validation_index]}"
            export LOGIN_TARO_VALIDATION_SECRET="${service_secret_values[$taro_validation_index]}"
        fi
        if [[ "$directory" == "customer-service" ]]; then
            export LOGIN_CUSTOMER_VALIDATION_SECRET="${service_secret_values[$customer_validation_index]}"
            export ALLOCATION_CUSTOMER_REFERENCE_SECRET="${service_secret_values[$customer_reference_index]}"
            export ALLOCATION_CUSTOMER_ASSERTION_SECRET="${service_secret_values[$customer_assertion_index]}"
        fi
        if [[ "$directory" == "dispatcher-service" ]]; then
            export LOGIN_DISPATCHER_VALIDATION_SECRET="${service_secret_values[$dispatcher_validation_index]}"
            export ALLOCATION_DISPATCHER_REFERENCE_SECRET="${service_secret_values[$dispatcher_reference_index]}"
            export ALLOCATION_DISPATCHER_ASSERTION_SECRET="${service_secret_values[$dispatcher_assertion_index]}"
        fi
        if [[ "$directory" == "allocation-service" ]]; then
            export LOGIN_ALLOCATION_VALIDATION_SECRET="${service_secret_values[$allocation_validation_index]}"
            export ALLOCATION_CUSTOMER_REFERENCE_SECRET="${service_secret_values[$customer_reference_index]}"
            export ALLOCATION_DISPATCHER_REFERENCE_SECRET="${service_secret_values[$dispatcher_reference_index]}"
            export ALLOCATION_TARO_REFERENCE_SECRET="${service_secret_values[$taro_reference_index]}"
            export ALLOCATION_CUSTOMER_ASSERTION_SECRET="${service_secret_values[$customer_assertion_index]}"
            export ALLOCATION_DISPATCHER_ASSERTION_SECRET="${service_secret_values[$dispatcher_assertion_index]}"
        fi
        if [[ "$directory" == "taro-service" ]]; then
            export LOGIN_TARO_VALIDATION_SECRET="${service_secret_values[$taro_validation_index]}"
            export ALLOCATION_TARO_REFERENCE_SECRET="${service_secret_values[$taro_reference_index]}"
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
