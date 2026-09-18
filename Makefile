.PHONY: help build test up down run-pricing run-telemetry run-dispatch run-gateway ping certs

help:
	@grep -E '^[a-z-]+:' Makefile | cut -d: -f1 | tail -n +2

build:          ; ./mvnw -q clean install -DskipTests
test:           ; ./mvnw test
up:             ; docker compose -f deploy/compose/docker-compose.yml up -d
down:           ; docker compose -f deploy/compose/docker-compose.yml down -v
certs:          ; ./deploy/certs/generate-certs.sh

# Khoi dong theo dung thu tu: tang duoi truoc, tang tren sau.
run-telemetry:  ; ./mvnw -pl telemetry-service spring-boot:run
run-pricing:    ; ./mvnw -pl pricing-service   spring-boot:run
run-dispatch:   ; ./mvnw -pl dispatch-service  spring-boot:run
run-gateway:    ; ./mvnw -pl edge-gateway      spring-boot:run

ping:           ; curl -s localhost:8080/api/v1/ping | jq .
