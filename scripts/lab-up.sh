#!/usr/bin/env bash
# Build the whole lab from Git with one command, and time every step.
#
#   layer 0 (this script):  kind cluster -> Calico CNI -> local registry -> images -> Argo CD
#   layer 1 (Argo CD, from Git): ingress-nginx, the shop services, Argo CD itself
#
# Idempotent: every step checks what already exists, so it is safe to re-run after a failure
# or on a running lab (it then only verifies). Images live in a Docker volume, so they survive
# `scripts/lab-down.sh`; only a first run on a new machine builds them.
#
# Usage: scripts/lab-up.sh
# Prereqs: docker, kind, kubectl, git, curl (run from a clone of this repo)
set -euo pipefail
cd "$(dirname "$0")/.."

CLUSTER=${CLUSTER:-lab}
CTX=kind-$CLUSTER
K="kubectl --context $CTX"
SERVICES="eureka:discovery-server user-service:user-service order-service:order-service api-gateway:api-gateway"

START=$(date +%s); T=$START; REPORT=""
step() { echo; echo "==> $*"; }
done_step() { local now; now=$(date +%s); REPORT+=$(printf '%-34s %4ss\n' "$1" $((now-T)))$'\n'; T=$now; }

# ---- 0. preflight -------------------------------------------------------------
step "preflight"
for bin in docker kind kubectl git curl; do
  command -v "$bin" >/dev/null || { echo "ERROR: '$bin' not found"; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "ERROR: Docker is not running"; exit 1; }
done_step "preflight"

# ---- 1. cluster -----------------------------------------------------------------
step "kind cluster '$CLUSTER'"
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "already exists"
else
  kind create cluster --config k8s/kind-cluster.yaml --name "$CLUSTER" --wait 0s
fi
# A cluster created before the Calico switch still runs kindnet (disableDefaultCNI only applies
# at creation). Two CNIs at once is undefined, and kindnet's policy agent is what wedged DNS
# when NetworkPolicies exist (ADR-0004). Never "upgrade" in place: recreate.
if $K -n kube-system get daemonset kindnet >/dev/null 2>&1; then
  echo "ERROR: cluster '$CLUSTER' still runs kindnet (created before the Calico switch)."
  echo "       Recreate it: scripts/lab-down.sh && scripts/lab-up.sh  (images are kept)"
  exit 1
fi
done_step "cluster"

# ---- 2. CNI: Calico (nodes are NotReady until pod networking exists) ----------
step "Calico CNI"
$K apply --server-side --force-conflicts -k k8s/platform/calico >/dev/null
$K -n kube-system rollout status ds/calico-node --timeout=300s
$K -n kube-system rollout status deploy/calico-kube-controllers --timeout=300s
$K wait --for=condition=Ready nodes --all --timeout=300s
done_step "calico + nodes Ready"

# ---- 3. local image registry, wired into every node -----------------------------
step "registry"
CLUSTER=$CLUSTER scripts/registry-up.sh
done_step "registry"

# ---- 4. images: the registry volume normally has them; build only what is missing --
step "images for the pinned tag"
TAG=$(awk '/newTag:/{gsub(/"/,"",$2); print $2; exit}' k8s/apps/kustomization.yaml)
ACCEPT='application/vnd.oci.image.index.v1+json,application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.v2+json'
missing=""
for pair in $SERVICES; do
  img=${pair%%:*}
  curl -sf -o /dev/null -H "Accept: $ACCEPT" "http://localhost:5001/v2/$img/manifests/$TAG" || missing+=" $pair"
done
if [ -n "$missing" ]; then
  echo "building$missing at $TAG"
  wt=$(mktemp -d); git worktree add --detach "$wt" "$TAG" >/dev/null
  trap 'git worktree remove --force "$wt" 2>/dev/null || true' EXIT
  for pair in $missing; do
    img=${pair%%:*}; dir=${pair#*:}
    ctx=(); [ -d "$wt/shop-parent" ] && ctx=(--build-context "parent=$wt/shop-parent")
    docker build "${ctx[@]}" -t "localhost:5001/$img:$TAG" "$wt/$dir"
    docker push "localhost:5001/$img:$TAG"
  done
else
  echo "all 4 images for $TAG already in the registry"
fi
done_step "images"

# ---- 5. Argo CD, then hand over to GitOps -----------------------------------------
step "Argo CD"
$K create namespace argocd --dry-run=client -o yaml | $K apply -f - >/dev/null
$K apply -n argocd --server-side --force-conflicts -k k8s/platform/argocd >/dev/null
$K wait --for=condition=Established crd/applications.argoproj.io --timeout=120s
$K -n argocd rollout status deploy/argocd-repo-server --timeout=300s
$K -n argocd rollout status statefulset/argocd-application-controller --timeout=300s
$K apply -f k8s/argocd/root.yaml
done_step "argo cd"

step "waiting for Argo CD to deploy ingress-nginx and shop from Git"
until $K -n argocd get application shop ingress-nginx >/dev/null 2>&1; do sleep 5; done
$K -n argocd wait application/ingress-nginx application/shop \
  --for=jsonpath='{.status.health.status}'=Healthy --timeout=900s
done_step "gitops sync (apps Healthy)"

# ---- 6. smoke test through the front door ------------------------------------------
step "smoke test: GET http://localhost/order/order"
# Pods are Healthy before Eureka has every registration, so allow ~3 min of retries.
ok=0
for i in $(seq 1 36); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost/order/order || true)
  if [ "$code" = 200 ]; then ok=$((ok+1)); [ $ok -ge 5 ] && break; else ok=0; fi
  sleep 5
done
[ $ok -ge 5 ] || { echo "ERROR: /order/order not stable (last status $code)"; exit 1; }
echo "5 consecutive 200s"
done_step "smoke test"

echo
echo "================ lab is up in $(( $(date +%s) - START ))s ================"
printf '%s' "$REPORT"
echo "Argo CD UI: $K -n argocd port-forward svc/argocd-server 8080:80  -> http://localhost:8080"
