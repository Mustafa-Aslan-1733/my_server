#!/bin/bash
# Ensures the app-server container has a healthy network attachment after boot.
# Workaround for its Docker bridge veth sometimes not reattaching cleanly on host reboot.
set -e

CONTAINER=app-server

for i in $(seq 1 30); do
  docker info >/dev/null 2>&1 && break
  sleep 1
done

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
  echo "app-server-netfix: container $CONTAINER not found, skipping"
  exit 0
fi

docker network disconnect bridge "$CONTAINER" --force >/dev/null 2>&1 || true
docker network connect bridge "$CONTAINER" >/dev/null 2>&1 || true
docker restart "$CONTAINER" >/dev/null 2>&1 || true

echo "app-server-netfix: reattached $CONTAINER to bridge network and restarted"
