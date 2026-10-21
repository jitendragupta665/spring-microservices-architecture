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
#   1. a pod in the cluster is running it            (deleting it breaks restarts)
#   2. k8s/apps/kustomization.yaml pins it           (Argo CD is about to deploy it)
#   3. its tag is one of the last $KEEP commits on main                       (fast rollback)
#      Git decides "newest", not image timestamps: unchanged code rebuilds to the SAME image
#      under a new tag, so timestamps tie. PR builds are tagged with GitHub's temporary
#      test-merge SHA, which is never on main, so those images are always cleaned up.
#
# Usage: scripts/lab-cleanup.sh            # dry run: only prints what would be deleted
#        scripts/lab-cleanup.sh --apply    # really delete
set -euo pipefail

APPLY=false; [ "${1:-}" = "--apply" ] && APPLY=true
KEEP=${KEEP:-3}
CLUSTER=${CLUSTER:-lab}
CACHE_MAX=${CACHE_MAX:-3gb}
SERVICES="eureka user-service order-service api-gateway"
KUSTOMIZATION=${KUSTOMIZATION:-k8s/apps/kustomization.yaml}

run() { if $APPLY; then "$@"; else echo "  [dry-run] $*"; fi; }

# ---- 1. build the KEEP set -------------------------------------------------
in_use=$(kubectl get pods -A -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{end}' \
         | sed 's#^docker.io/library/##' | sort -u)
# Read the pin from the LATEST main (fetched), not from this run's checkout: an older run
# that finishes after a newer merge must still protect the newer pinned tag.
pinned=$( (git show "origin/main:$KUSTOMIZATION" 2>/dev/null || cat "$KUSTOMIZATION" 2>/dev/null || true) \
          | awk '/- name:/{n=$3} /newTag:/{gsub(/"/,"",$2); print n":"$2}')
recent=$(git log --first-parent -n "$KEEP" --format=%H origin/main 2>/dev/null \
         || git log --first-parent -n "$KEEP" --format=%H HEAD)
newest=""
for s in $SERVICES; do
  for sha in $recent; do newest+="$s:${sha::7}"$'\n'; done
done
keep=$(printf '%s\n%s\n%s\n' "$in_use" "$pinned" "$newest" | grep -v '^$' | sort -u)

echo "== keep set ($(echo "$keep" | wc -l) images)"; echo "$keep" | grep -E "^($(echo $SERVICES | tr ' ' '|')):" | sed 's/^/  keep /'

is_kept() { grep -qxF "$1" <<<"$keep"; }

# ---- 2. Docker host ---------------------------------------------------------
echo "== docker host"
for s in $SERVICES; do
  for img in $(docker images "$s" --format '{{.Repository}}:{{.Tag}}' | grep -v '<none>'); do
    is_kept "$img" || run docker rmi "$img"
  done
done

# ---- 3. kind nodes (containerd inside each node container) ------------------
for node in $(kind get nodes --name "$CLUSTER"); do
  echo "== node $node"
  for img in $(docker exec "$node" crictl images | awk 'NR>1{print $1":"$2}' \
               | sed 's#^docker.io/library/##' | grep -E "^($(echo $SERVICES | tr ' ' '|')):"); do
    # NOT `crictl rmi`: CRI RemoveImage deletes the image ID with ALL its tags, which would also
    # remove a kept tag pointing at the same (unchanged) image. `ctr images rm` deletes only
    # this one name; containerd garbage-collects the content once no name references it.
    is_kept "$img" || run docker exec "$node" ctr -n k8s.io images rm "docker.io/library/$img"
  done
done

# ---- 4. BuildKit cache: keep it warm but bounded ----------------------------
echo "== build cache (cap at $CACHE_MAX)"
run docker builder prune -f --max-used-space "$CACHE_MAX"

$APPLY && { echo "== after"; docker system df; } || echo "== dry run only. Re-run with --apply to delete."
