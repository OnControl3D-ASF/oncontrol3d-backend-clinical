#!/usr/bin/env bash
# Renders deploy/k8s for dev and prod with placeholder values and validates them with
# kubeconform (strict). Same script locally and in CI (ci.yml, job "manifests").
#
#   deploy/validate.sh
#
# Uses a local `kubeconform` when present, otherwise the pinned container image.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
KUBECONFORM_IMAGE="${KUBECONFORM_IMAGE:-ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e}"
K8S_VERSION="1.33.0"

export IMAGE="example.azurecr.io/oncontrol3d/clinical@sha256:0000000000000000000000000000000000000000000000000000000000000000"
export APP_VERSION="ci-validation"
export WORKLOAD_IDENTITY_CLIENT_ID="00000000-0000-0000-0000-000000000000"
export TENANT_ID="00000000-0000-0000-0000-000000000000"
export KEY_VAULT_NAME="kv-validation"
export POSTGRES_FQDN="psql-validation.postgres.database.azure.com"
export DB_USERNAME="validation"
export SERVICEBUS_NAMESPACE="sb-validation.servicebus.windows.net"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

for env in dev prod; do
  "$DIR/render.sh" "$env" > "$OUT/$env.yaml"
done

# SecretProviderClass is a CRD without a public schema: skipped, everything else is strict.
if command -v kubeconform >/dev/null 2>&1; then
  kubeconform -strict -summary -ignore-missing-schemas -kubernetes-version "$K8S_VERSION" "$OUT/dev.yaml" "$OUT/prod.yaml"
else
  docker run --rm -v "$OUT:/m:ro" "$KUBECONFORM_IMAGE" \
    -strict -summary -ignore-missing-schemas -kubernetes-version "$K8S_VERSION" /m/dev.yaml /m/prod.yaml
fi
