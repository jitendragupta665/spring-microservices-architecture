#!/usr/bin/env bash
# Local container registry for the kind lab ("layer 0" infra, like the cluster itself:
# it must exist BEFORE Argo CD can deploy anything, so it is a script, not an Argo app).
#
#   laptop / CI runners  --docker push-->  localhost:5001  (published on 127.0.0.1 only)
#   kind nodes (containerd) --pull-->  "localhost:5001/..."  --hosts.toml-->  http://kind-registry:5000
#
# Inside a node, "localhost" is the node itself, so each node gets a containerd hosts.toml that
# redirects localhost:5001 to the registry container's name on the shared "kind" Docker network.
# Same design as https://kind.sigs.k8s.io/docs/user/local-registry/
#
# Idempotent: safe to re-run (after a cluster rebuild, a laptop reboot, etc.).
# Usage: scripts/registry-up.sh
set -euo pipefail

CLUSTER=${CLUSTER:-lab}
REG_NAME=kind-registry
REG_PORT=5001
REG_IMAGE=${REG_IMAGE:-registry:3.1.2}        # pinned; distribution/distribution v3
REG_VOLUME=kind-registry-data                 # images survive container re-creation

# ---- 1. registry container --------------------------------------------------
if [ "$(docker inspect -f '{{.State.Running}}' "$REG_NAME" 2>/dev/null || true)" != "true" ]; then
  docker rm -f "$REG_NAME" >/dev/null 2>&1 || true
  echo "== starting $REG_NAME ($REG_IMAGE) on 127.0.0.1:$REG_PORT"
  docker run -d --restart=always --name "$REG_NAME" \
    -p "127.0.0.1:${REG_PORT}:5000" \
    -v "${REG_VOLUME}:/var/lib/registry" \
    -e REGISTRY_STORAGE_DELETE_ENABLED=true \
    "$REG_IMAGE" >/dev/null
else
  echo "== $REG_NAME already running"
fi

# ---- 2. join the kind network so nodes can reach it by name -----------------
if [ "$(docker inspect -f '{{json .NetworkSettings.Networks.kind}}' "$REG_NAME")" = "null" ]; then
  docker network connect kind "$REG_NAME"
  echo "== connected $REG_NAME to the kind network"
fi

# ---- 3. each node: containerd reads /etc/containerd/certs.d, localhost:5001 -> registry
nodes=$(kind get nodes --name "$CLUSTER")
[ -n "$nodes" ] || { echo "ERROR: no nodes for kind cluster $CLUSTER"; exit 1; }
for node in $nodes; do
  dir="/etc/containerd/certs.d/localhost:${REG_PORT}"
  docker exec "$node" mkdir -p "$dir"
  printf '[host."http://%s:5000"]\n' "$REG_NAME" | docker exec -i "$node" cp /dev/stdin "$dir/hosts.toml"
  # Clusters created from k8s/kind-cluster.yaml already have config_path (containerdConfigPatches).
  # Older clusters don't: add it once and restart containerd. Running containers survive a
  # containerd restart (each one is held by its own containerd-shim).
  if ! docker exec "$node" grep -q 'config_path = "/etc/containerd/certs.d"' /etc/containerd/config.toml; then
    printf '\n[plugins."io.containerd.grpc.v1.cri".registry]\n  config_path = "/etc/containerd/certs.d"\n' \
      | docker exec -i "$node" sh -c 'cat >> /etc/containerd/config.toml'
    docker exec "$node" systemctl restart containerd
    echo "== $node: config_path added, containerd restarted"
  fi
  echo "== $node: localhost:${REG_PORT} -> http://${REG_NAME}:5000"
done

# ---- 4. smoke test ------------------------------------------------------------
curl -sf "http://localhost:${REG_PORT}/v2/" >/dev/null && echo "== registry API reachable at http://localhost:${REG_PORT}/v2/"
