#!/bin/bash
# Build the app image locally, ship it to the production server over SSH,
# then push the code so the server's post-receive hook restarts the container.
#
# Usage: ./deploy.sh
set -e

SSH_KEY="$HOME/deploy_ci_key"
SERVER="project@10.198.200.84"
IMAGE="hrcp-academic:latest"
DB_IMAGE="postgres:16"

ssh_run() { ssh -i "$SSH_KEY" "$SERVER" "$@"; }

# Postgres runs as its own container now. The server has no outbound internet,
# so it cannot pull from Docker Hub — ship the image the first time only.
# Shipping it on every deploy would waste ~150MB over the link for nothing.
echo "--- Checking Postgres image on server ---"
if ssh_run "docker image inspect $DB_IMAGE > /dev/null 2>&1"; then
    echo "already present, skipping"
else
    echo "not present, shipping (first deploy only, ~150MB)"
    docker pull "$DB_IMAGE"
    docker save "$DB_IMAGE" | ssh_run "docker load"
fi

echo "--- Building image locally ---"
docker build -t "$IMAGE" ./HRCP-KKU-Academic

echo "--- Shipping image to server ---"
docker save "$IMAGE" | ssh_run "docker load"

echo "--- Pushing code to trigger restart ---"
GIT_SSH_COMMAND="ssh -i $SSH_KEY" git push production main

echo "--- Done ---"
