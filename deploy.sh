#!/bin/bash
# Build the app image locally, ship it to the production server over SSH,
# then push the code so the server's post-receive hook restarts the container.
#
# Usage: ./deploy.sh
set -e

SSH_KEY="$HOME/deploy_ci_key"
SERVER="project@10.198.200.84"
IMAGE="hrcp-academic:latest"

echo "--- Building image locally ---"
docker build -t "$IMAGE" ./HRCP-KKU-Academic

echo "--- Shipping image to server ---"
docker save "$IMAGE" | ssh -i "$SSH_KEY" "$SERVER" "docker load"

echo "--- Pushing code to trigger restart ---"
GIT_SSH_COMMAND="ssh -i $SSH_KEY" git push production main

echo "--- Done ---"
