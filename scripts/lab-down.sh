#!/usr/bin/env bash
# Delete the kind cluster. Kept on purpose: the registry container and its volume (all built
# images), the build cache and the CI runners. `scripts/lab-up.sh` rebuilds the cluster from Git.
#
# Usage: scripts/lab-down.sh
set -euo pipefail
CLUSTER=${CLUSTER:-lab}
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  kind delete cluster --name "$CLUSTER"
else
  echo "no kind cluster named $CLUSTER"
fi
