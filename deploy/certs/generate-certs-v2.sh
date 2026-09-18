#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

rm -f ./*.csr ./*.srl

openssl req \
  -x509 \
  -newkey rsa:4096 \
  -sha256 \
  -days 365 \
  -nodes \
  -keyout ca-key.pem \
  -out ca-cert.pem \
  -subj "/CN=ServiceLab Internal CA"

for svc in \
  edge-gateway \
  dispatch-service \
  pricing-service \
  telemetry-service
do
  openssl req \
    -newkey rsa:2048 \
    -sha256 \
    -nodes \
    -keyout "${svc}-key.pem" \
    -out "${svc}.csr" \
    -subj "/CN=${svc}.servicelab.internal"

  openssl x509 \
    -req \
    -in "${svc}.csr" \
    -sha256 \
    -days 365 \
    -CA ca-cert.pem \
    -CAkey ca-key.pem \
    -CAcreateserial \
    -extfile <(
      printf \
        "subjectAltName=DNS:%s.servicelab.internal,DNS:%s,DNS:localhost,IP:127.0.0.1" \
        "$svc" \
        "$svc"
    ) \
    -out "${svc}-cert.pem"

  rm -f "${svc}.csr"
done

# RSA key pair rieng cho internal JWT.
# Khong dung TLS private key de ky token.
openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:3072 \
  -out gateway-token-key.pem

openssl pkey \
  -in gateway-token-key.pem \
  -pubout \
  -out gateway-token-pub.pem

chmod 600 \
  ca-key.pem \
  ./*-key.pem \
  gateway-token-key.pem

chmod 644 \
  ca-cert.pem \
  ./*-cert.pem \
  gateway-token-pub.pem

echo "Da sinh certificate va JWT key trong: $(pwd)"