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
REGISTRY=${REGISTRY:-localhost:5001}
# One image can carry several names: eureka:abc1234, docker.io/library/eureka:abc1234,
# localhost:5001/eureka:abc1234. Keep decisions use the short form; deletions use the full name.
short() { sed -e 's#^docker.io/library/##' -e "s#^${REGISTRY}/##"; }
KUSTOMIZATION=${KUSTOMIZATION:-k8s/apps/kustomization.yaml}

run() { if $APPLY; then "$@"; else echo "  [dry-run] $*"; fi; }

# Every list is captured into a variable first: `x=$(cmd)` fails the script under set -e/pipefail,
# while `for i in $(cmd)` would silently loop over nothing if cmd failed.

# ---- 1. build the KEEP set -------------------------------------------------
in_use=$(kubectl --context "$KCTX" get pods -A \
           -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{end}' \
         | short | sort -u)
[ -n "$in_use" ] || { echo "ERROR: no running images found in $KCTX, refusing to prune"; exit 1; }

# Pin comes from the LATEST main (fetched), not this run's checkout: an older run that finishes
# after a newer merge must still protect the newer pinned tag.
kustom=$(git show "origin/main:$KUSTOMIZATION" 2>/dev/null || cat "$KUSTOMIZATION")
pinned=$(awk '/- name:/{n=$3} /newTag:/{gsub(/"/,"",$2); print n":"$2}' <<<"$kustom")

commits=$(git log --first-parent -n 30 --format=%H origin/main 2>/dev/null \
          || git log --first-parent -n 30 --format=%H HEAD)
newest=""
REG_URL=${REG_URL:-http://localhost:5001}
# An image counts as "existing" if it is on this Docker host OR in the registry. The registry is
# the source of truth for rollback; local images are only a cache of it.
reg_has() { curl -fsI -o /dev/null "$REG_URL/v2/$1/manifests/$2" 2>/dev/null; }
for s in $SERVICES; do
  n=0
  for sha in $commits; do
    if docker image inspect "$s:${sha::7}" >/dev/null 2>&1 || reg_has "$s" "${sha::7}"; then
      newest+="$s:${sha::7}"$'\n'; n=$((n+1)); [ "$n" -ge "$KEEP" ] && break
    fi
  done
done

keep=$(printf '%s\n%s\n%s\n' "$in_use" "$pinned" "$newest" | grep -v '^$' | sort -u)
echo "== keep set"; grep -E "$SVC_RE" <<<"$keep" | sed 's/^/  keep /'
is_kept() { grep -qxF "$1" <<<"$keep"; }

# ---- 2. Docker host ---------------------------------------------------------
echo "== docker host"
host_imgs=$(docker images --format '{{.Repository}}:{{.Tag}}' | grep -v '<none>' || true)
for img in $host_imgs; do
  s=$(short <<<"$img"); grep -qE "$SVC_RE" <<<"$s" || continue
  is_kept "$s" || run docker rmi "$img"     # with several tags, rmi only untags this one
done

# ---- 3. kind nodes (containerd inside each node container) ------------------
nodes=$(kind get nodes --name "$CLUSTER")
[ -n "$nodes" ] || { echo "ERROR: no nodes for kind cluster $CLUSTER"; exit 1; }
for node in $nodes; do
  echo "== node $node"
  node_imgs=$(docker exec "$node" crictl images | awk 'NR>1{print $1":"$2}')
  for ref in $node_imgs; do
    img=$(short <<<"$ref"); grep -qE "$SVC_RE" <<<"$img" || continue
    # NOT `crictl rmi`: CRI RemoveImage deletes the image ID with ALL its tags, which would also
    # remove a kept tag pointing at the same (unchanged) image. `ctr images rm` deletes only
    # this one name; containerd garbage-collects the content once no name references it.
    is_kept "$img" || run docker exec "$node" ctr -n k8s.io images rm "$ref"
  done
done

# ---- 4. local registry (localhost:5001) -------------------------------------
# `docker rmi localhost:5001/...` only drops the client's local name; the registry keeps the
# manifest and blobs. Retention is applied through the registry API, then garbage collection.
# The API deletes by DIGEST, and a digest delete removes EVERY tag pointing at it. Unchanged
# services rebuild to identical images, so an old tag can share a digest with a kept one:
# never delete a digest that any kept tag uses.
REG_CONTAINER=${REG_CONTAINER:-kind-registry}
ACCEPT='application/vnd.oci.image.index.v1+json,application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.docker.distribution.manifest.v2+json'
digest_of() {
  curl -fsSI -H "Accept: $ACCEPT" "$REG_URL/v2/$1/manifests/$2" \
    | awk 'tolower($1)=="docker-content-digest:"{print $2}' | tr -d '\r'
}
if curl -fs "$REG_URL/v2/" >/dev/null 2>&1; then
  echo "== registry $REG_URL"
  for s in $SERVICES; do
    tags=$(curl -fs "$REG_URL/v2/$s/tags/list" 2>/dev/null \
           | python3 -c 'import json,sys; print(" ".join(json.load(sys.stdin).get("tags") or []))') || continue
    # Resolve every tag's digest BEFORE deleting anything: once a digest is deleted, the other
    # tags that pointed at it are gone too, and looking them up would 404.
    unset dig; declare -A dig=()
    for t in $tags; do dig[$t]=$(digest_of "$s" "$t" || true); done
    kept_digests=""; unsafe=false
    for t in $tags; do
      is_kept "$s:$t" || continue
      if [ -z "${dig[$t]:-}" ]; then
        echo "  ERROR: cannot resolve digest of kept tag $s:$t, skipping registry pruning for $s"
        unsafe=true; break
      fi
      kept_digests+=" ${dig[$t]}"
    done
    $unsafe && continue
    deleted=""
    for t in $tags; do
      is_kept "$s:$t" && continue
      d=${dig[$t]}; [ -n "$d" ] || continue
      if grep -qF -- "$d" <<<"$kept_digests"; then echo "  keep $s:$t (same image as a kept tag)"; continue; fi
      grep -qF -- "$d" <<<"$deleted" && continue          # already deleted via another tag
      run curl -fsS -o /dev/null -X DELETE "$REG_URL/v2/$s/manifests/$d"
      deleted+=" $d"
    done
  done
  # Frees the blobs no manifest references any more. GC must not race a push (a blob uploaded
  # but not yet linked to a manifest looks unreferenced), so the registry is STOPPED while it
  # runs and always restarted. Pulls fail for a few seconds; kubelet retries them.
  REG_IMAGE=$(docker inspect -f '{{.Config.Image}}' "$REG_CONTAINER")
  if $APPLY; then
    docker stop "$REG_CONTAINER" >/dev/null
    trap 'docker start "$REG_CONTAINER" >/dev/null' EXIT
    # Capture the collector's output and exit status separately: only grep's "no match" may be
    # tolerated, a failing collector must fail the cleanup (the trap still restarts the registry).
    gc_out=$(docker run --rm --volumes-from "$REG_CONTAINER" "$REG_IMAGE" \
               garbage-collect --delete-untagged /etc/distribution/config.yml 2>&1) \
      || { echo "ERROR: registry garbage-collect failed:"; echo "$gc_out"; exit 1; }
    n=$(grep -c 'Deleting blob:' <<<"$gc_out" || true)
    docker start "$REG_CONTAINER" >/dev/null; trap - EXIT
    echo "  garbage-collect: $n blobs deleted"
  else
    echo "  [dry-run] stop $REG_CONTAINER; garbage-collect --delete-untagged; start $REG_CONTAINER"
  fi
else
  echo "== registry $REG_URL not reachable, skipped"
fi

# ---- 5. BuildKit cache: keep it warm but bounded ----------------------------
echo "== build cache (cap at $CACHE_MAX)"
run docker builder prune -f --max-used-space "$CACHE_MAX"

if $APPLY; then echo "== after"; docker system df; else echo "== dry run only. Re-run with --apply to delete."; fi
