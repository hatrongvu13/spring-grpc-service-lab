#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

openssl req -x509 -newkey rsa:4096 -days 365 -nodes \
  -keyout ca-key.pem -out ca-cert.pem \
  -subj "/CN=FleetPulse Internal CA"

for svc in edge-gateway dispatch-service pricing-service telemetry-service; do
  openssl req -newkey rsa:2048 -nodes \
    -keyout "${svc}-key.pem" -out "${svc}.csr" \
    -subj "/CN=${svc}.servicelab.internal"
  openssl x509 -req -in "${svc}.csr" -days 365 \
    -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial \
    -extfile <(printf "subjectAltName=DNS:%s.servicelab.internal,DNS:localhost" "$svc") \
    -out "${svc}-cert.pem"
  rm -f "${svc}.csr"
done

# Khoa ky internal token cua gateway (Ed25519).
openssl genpkey -algorithm ED25519 -out gateway-token-key.pem
openssl pkey -in gateway-token-key.pem -pubout -out gateway-token-pub.pem

echo "Da sinh cert va khoa trong $(pwd)"
