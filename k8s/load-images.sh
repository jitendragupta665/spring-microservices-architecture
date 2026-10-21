#!/bin/bash
# Tag local images with a fixed version and copy them into every kind node's containerd.
set -e
VERSION=1.0
for svc in eureka user-service order-service api-gateway; do
  docker tag "spring-microservices-architecture-${svc}:latest" "${svc}:${VERSION}"
  echo "[$(date +%T)] loading ${svc}:${VERSION} into cluster 'lab' ..."
  kind load docker-image "${svc}:${VERSION}" --name lab
done
echo "[$(date +%T)] ALL DONE"
