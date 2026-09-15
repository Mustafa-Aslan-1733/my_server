#!/usr/bin/env bash
# Weekly removal of Docker images that no container is using.
#
# Every push to main tags an image with its commit SHA and the deploy pulls it onto this
# 11 GB disk. Nothing ever removed the old ones: by 2026-09-06 there were ten images with
# 500 MB of them reclaimable, on a disk that was 70% full.
#
# `docker image prune` never touches an image that a container references -- running or
# stopped -- so postgres:16 and whatever app-server and admin-web-prod are currently on
# are safe by construction rather than by a list kept in this file that would go stale.
#
# Volumes are deliberately NOT pruned here. An automated volume prune is the kind of job
# that deletes live data on the day someone stops a container to debug it.

set -euo pipefail

# Builds younger than this stay on disk, so a rollback to a recent image needs no network.
# Older tags are not lost: they are in the GitLab registry and come back with docker pull.
KEEP_HOURS="${KEEP_HOURS:-72}"

usage() { docker system df --format '{{.Type}}\t{{.Size}}\t{{.Reclaimable}}' | awk -F'\t' '$1=="Images"{print $2" total, "$3" reclaimable"}'; }

echo "before: $(usage)"
docker image prune --all --force --filter "until=${KEEP_HOURS}h"
echo "after:  $(usage)"
echo "kept every image newer than ${KEEP_HOURS}h and every image a container references"
