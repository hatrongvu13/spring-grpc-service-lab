#!/usr/bin/env bash
for svc in \
  dispatch-service \
  pricing-service \
  telemetry-service
do
  mkdir -p "${svc}/src/main/resources/keys"

  cp deploy/certs/gateway-token-pub.pem \
    "${svc}/src/main/resources/keys/internal-public.pem"
done

cp cp deploy/certs/gateway-token-key.pem \