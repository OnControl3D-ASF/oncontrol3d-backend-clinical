#!/usr/bin/env bash
# Renders deploy/k8s/clinical.yaml for an environment.
#
#   deploy/render.sh <dev|prod>
#
# Non-secret sizing comes from deploy/env/<env>.env. The pipeline exports the rest from the
# IaC deployment outputs: IMAGE, APP_VERSION, WORKLOAD_IDENTITY_CLIENT_ID, TENANT_ID,
# KEY_VAULT_NAME, POSTGRES_FQDN, DB_USERNAME, SERVICEBUS_NAMESPACE.
# Only the listed variables are substituted, and every one of them must be set.
set -euo pipefail

ENVIRONMENT="${1:?usage: deploy/render.sh <dev|prod>}"
DIR="$(cd "$(dirname "$0")" && pwd)"

set -a
# shellcheck disable=SC1090
. "$DIR/env/$ENVIRONMENT.env"
set +a

REQUIRED=(NAMESPACE IMAGE APP_VERSION MIN_REPLICAS MAX_REPLICAS CPU_REQUEST CPU_LIMIT MEMORY_REQUEST
          MEMORY_LIMIT SEEDING_ENABLED WORKLOAD_IDENTITY_CLIENT_ID TENANT_ID KEY_VAULT_NAME
          POSTGRES_FQDN DB_NAME DB_USERNAME SERVICEBUS_NAMESPACE)
for name in "${REQUIRED[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "render.sh: required variable $name is empty" >&2
    exit 1
  fi
done

# shellcheck disable=SC2016 # literal ${VAR} list is what envsubst expects
envsubst "$(printf '${%s} ' "${REQUIRED[@]}")" < "$DIR/k8s/clinical.yaml"
