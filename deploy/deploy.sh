#!/usr/bin/env bash
# Deploys the images CI published for one commit, pinned BY DIGEST (a tag can be moved; a digest is the
# content itself), so the server runs byte for byte what CI tested.
# Usage (on the server, in this folder):  ./deploy.sh <full commit sha>
set -euo pipefail
cd "$(dirname "$0")"

SHA="${1:?usage: ./deploy.sh <full 40-character commit sha>}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "Give the FULL commit sha (40 hex characters)" >&2; exit 1; }
[[ -f .env ]] || { echo "Missing .env (copy .env.example and fill it in)" >&2; exit 1; }
REGISTRY="ghcr.io/bossshhh"

digest_of() {
  local ref="$REGISTRY/stockflow-$1:$SHA"
  docker pull --quiet "$ref" > /dev/null || { echo "No published image $ref: did CI pass for this commit?" >&2; exit 1; }
  docker image inspect --format '{{index .RepoDigests 0}}' "$ref"
}

set_env() {  # set_env KEY VALUE: replace or append KEY=VALUE in .env
  if grep -q "^$1=" .env; then sed -i.bak "s|^$1=.*|$1=$2|" .env && rm -f .env.bak; else echo "$1=$2" >> .env; fi
}

API_IMAGE="$(digest_of api)"
WEB_IMAGE="$(digest_of web)"
set_env API_IMAGE "$API_IMAGE"
set_env WEB_IMAGE "$WEB_IMAGE"
set_env DEPLOYED_COMMIT "$SHA"

docker compose up -d --wait --wait-timeout 300
echo "Deployed commit $SHA"
echo "  $API_IMAGE"
echo "  $WEB_IMAGE"
