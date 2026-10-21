#!/usr/bin/env bash
# Lab cleanup: remove old CI-built app images from the Docker host and from the kind nodes,
# and cap the BuildKit cache.
#
# Why this exists:
#   - Every merge to main builds 4 new images (<service>:<sha7>) and loads them into every node.
#   - Nothing deletes them: kubelet image GC only starts at 85% node disk, and kind nodes see the
#     whole ~1 TB virtual disk, so in practice it never runs.
#   - In production, a registry retention policy + kubelet GC + ephemeral runners do this job.
#
# An image is KEPT if any of these is true (everything else for our 4 services is deleted):
#   1. a pod in the lab cluster is running it          (deleting it breaks restarts)
#   2. k8s/apps/kustomization.yaml on origin/main pins it   (Argo CD is about to deploy it)
#   3. it is one of the newest $KEEP main commits whose image ACTUALLY EXISTS for that service
#      (fast rollback). Git decides "newest", not image timestamps: unchanged code rebuilds to
#      the SAME image under a new tag, so timestamps tie. Commits whose build failed or is still
#      queued have no image and are skipped, so they never push a real rollback image out.
#      PR builds use GitHub's temporary test-merge SHA (never on main), so they are always collected.
#
# Usage: scripts/lab-cleanup.sh            # dry run: only prints what would be deleted
#        scripts/lab-cleanup.sh --apply    # really delete
set -euo pipefail

APPLY=false; [ "${1:-}" = "--apply" ] && APPLY=true
KEEP=${KEEP:-3}
CLUSTER=${CLUSTER:-lab}
KCTX=${KCTX:-kind-$CLUSTER}      # always query the SAME cluster we prune, never the current context
CACHE_MAX=${CACHE_MAX:-3gb}      # docker 29+: `builder prune --max-used-space` (older CLIs: --keep-storage)
SERVICES="eureka user-service order-service api-gateway"
SVC_RE="^($(echo $SERVICES | tr ' ' '|')):"
KUSTOMIZATION=${KUSTOMIZATION:-k8s/apps/kustomization.yaml}

run() { if $APPLY; then "$@"; else echo "  [dry-run] $*"; fi; }

# Every list is captured into a variable first: `x=$(cmd)` fails the script under set -e/pipefail,
# while `for i in $(cmd)` would silently loop over nothing if cmd failed.

# ---- 1. build the KEEP set -------------------------------------------------
in_use=$(kubectl --context "$KCTX" get pods -A \
           -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{end}' \
         | sed 's#^docker.io/library/##' | sort -u)
[ -n "$in_use" ] || { echo "ERROR: no running images found in $KCTX, refusing to prune"; exit 1; }

# Pin comes from the LATEST main (fetched), not this run's checkout: an older run that finishes
# after a newer merge must still protect the newer pinned tag.
kustom=$(git show "origin/main:$KUSTOMIZATION" 2>/dev/null || cat "$KUSTOMIZATION")
pinned=$(awk '/- name:/{n=$3} /newTag:/{gsub(/"/,"",$2); print n":"$2}' <<<"$kustom")

commits=$(git log --first-parent -n 30 --format=%H origin/main 2>/dev/null \
          || git log --first-parent -n 30 --format=%H HEAD)
newest=""
for s in $SERVICES; do
  n=0
  for sha in $commits; do
    if docker image inspect "$s:${sha::7}" >/dev/null 2>&1; then
      newest+="$s:${sha::7}"$'\n'; n=$((n+1)); [ "$n" -ge "$KEEP" ] && break
    fi
  done
done

keep=$(printf '%s\n%s\n%s\n' "$in_use" "$pinned" "$newest" | grep -v '^$' | sort -u)
echo "== keep set"; grep -E "$SVC_RE" <<<"$keep" | sed 's/^/  keep /'
is_kept() { grep -qxF "$1" <<<"$keep"; }

# ---- 2. Docker host ---------------------------------------------------------
echo "== docker host"
host_imgs=$(docker images --format '{{.Repository}}:{{.Tag}}' | awk -v re="$SVC_RE" '$0 ~ re')
for img in $host_imgs; do
  is_kept "$img" || run docker rmi "$img"     # with several tags, rmi only untags this one
done

# ---- 3. kind nodes (containerd inside each node container) ------------------
nodes=$(kind get nodes --name "$CLUSTER")
[ -n "$nodes" ] || { echo "ERROR: no nodes for kind cluster $CLUSTER"; exit 1; }
for node in $nodes; do
  echo "== node $node"
  node_imgs=$(docker exec "$node" crictl images | awk 'NR>1{print $1":"$2}' \
              | sed 's#^docker.io/library/##' | awk -v re="$SVC_RE" '$0 ~ re')
  for img in $node_imgs; do
    # NOT `crictl rmi`: CRI RemoveImage deletes the image ID with ALL its tags, which would also
    # remove a kept tag pointing at the same (unchanged) image. `ctr images rm` deletes only
    # this one name; containerd garbage-collects the content once no name references it.
    is_kept "$img" || run docker exec "$node" ctr -n k8s.io images rm "docker.io/library/$img"
  done
done

# ---- 4. BuildKit cache: keep it warm but bounded ----------------------------
echo "== build cache (cap at $CACHE_MAX)"
run docker builder prune -f --max-used-space "$CACHE_MAX"

if $APPLY; then echo "== after"; docker system df; else echo "== dry run only. Re-run with --apply to delete."; fi
