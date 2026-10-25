#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

service_pids=()

start_service() {
    local name="$1"
    local directory="$2"
    local pid

    (
        cd "$ROOT_DIR/$directory"
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

start_service customer-service customer-service
start_service taro-service taro-service
start_service allocation-service allocation-service

printf '\nServices are starting. Press Ctrl+C to stop all of them.\n'
printf 'Customer:    http://localhost:8081\nTaro:        http://localhost:8083\nAllocations: http://localhost:8082\n\n'
printf 'Streaming service logs below.\n'
tail -n 20 -f "$LOG_DIR/customer-service.log" "$LOG_DIR/taro-service.log" "$LOG_DIR/allocation-service.log" &
tail_pid=$!
service_pids+=("$tail_pid")

wait "$tail_pid"
