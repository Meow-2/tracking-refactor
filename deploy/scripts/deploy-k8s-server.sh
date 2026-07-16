#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

NFS_HOST="${NFS_HOST:-172.16.200.203}"
NFS_USER="${NFS_USER:-root}"
NFS_PORT="${NFS_PORT:-22}"
NFS_DEPLOY_DIR="${NFS_DEPLOY_DIR:-/opt/anyang/tracking}"
NFS_BUILD_SCRIPT="${NFS_BUILD_SCRIPT:-./build-push.sh}"
NFS_SSH_PASS="${NFS_SSH_PASS:-Aygg@2026}"

K8S_HOST="${K8S_HOST:-172.16.200.36}"
K8S_USER="${K8S_USER:-root}"
K8S_PORT="${K8S_PORT:-22}"
K8S_NAMESPACE="${K8S_NAMESPACE:-tracking}"
K8S_MANIFEST_DIR="${K8S_MANIFEST_DIR:-/data/mainfest/tracking}"
K8S_ROLLOUT_TIMEOUT="${K8S_ROLLOUT_TIMEOUT:-300s}"
K8S_SSH_PASS="${K8S_SSH_PASS:-Aygg@2026}"

MAVEN_BIN="${MAVEN_BIN:-mvn}"
MAVEN_SETTINGS_PATH="${MAVEN_SETTINGS_PATH:-${HOME}/.m2/settings.xml}"
MAVEN_LOCAL_REPOSITORY="${MAVEN_LOCAL_REPOSITORY:-${HOME}/.m2/repository}"
SKIP_TESTS="${SKIP_TESTS:-true}"

LOCAL_K8S_DIR="${LOCAL_K8S_DIR:-${PROJECT_DIR}/deploy/k8s}"
LOCAL_DOCKER_DIR="${LOCAL_DOCKER_DIR:-${PROJECT_DIR}/deploy/docker}"

UNITS=("baf1" "cbl1" "cp1" "csl1" "dcl1" "fcl1" "zrm1")
RUN_ID="$(date +%y%m%d-%H%M)-$$"

log() {
  printf '[deploy-tracking] %s\n' "$*"
}

die() {
  printf '[deploy-tracking] ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  .zed/scripts/deploy-k8s-server.sh

Required environment variables:
  NFS_SSH_PASS  Build server SSH password
  K8S_SSH_PASS  Kubernetes control host SSH password

This script builds and pushes one tracking image, synchronizes deploy/docker to
the build server, and synchronizes deploy/k8s to:
  /data/mainfest/tracking

Then it applies the manifests and restarts:
  tracking-baf1
  tracking-cbl1
  tracking-cp1
  tracking-csl1
  tracking-dcl1
  tracking-fcl1
  tracking-zrm1

Each ConfigMap must set its own TRACKING_UNIT environment variable.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

require_env() {
  local key="$1"
  [[ -n "${!key:-}" ]] || die "Missing required environment variable: ${key}"
}

remote_bash() {
  local password="$1" user="$2" host="$3" port="$4" command="$5"

  SSHPASS="${password}" sshpass -e ssh \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -p "${port}" \
    "${user}@${host}" \
    "bash -lc $(printf '%q' "${command}")"
}

copy_to_remote() {
  local password="$1" user="$2" host="$3" port="$4" source_path="$5" remote_path="$6"

  SSHPASS="${password}" sshpass -e scp \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -P "${port}" \
    "${source_path}" \
    "${user}@${host}:${remote_path}"
}

build_jar() {
  local -a command=("${MAVEN_BIN}")

  [[ ! -f "${MAVEN_SETTINGS_PATH}" ]] || command+=("-s" "${MAVEN_SETTINGS_PATH}")
  [[ -z "${MAVEN_LOCAL_REPOSITORY}" ]] ||
    command+=("-Dmaven.repo.local=${MAVEN_LOCAL_REPOSITORY}")
  command+=("clean" "package")

  case "${SKIP_TESTS}" in
    true|TRUE|1|yes|YES|y|Y) command+=("-DskipTests") ;;
  esac

  log "Maven command: ${command[*]}"
  (cd "${PROJECT_DIR}" && "${command[@]}")
}

find_jar() {
  local -a jars=()

  while IFS= read -r -d '' jar; do
    jars+=("${jar}")
  done < <(find "${PROJECT_DIR}/target" -maxdepth 1 -type f \
    -name 'tracking-*.jar' \
    ! -name '*.original' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print0)

  [[ ${#jars[@]} -eq 1 ]] ||
    die "Expected exactly one tracking Jar, found ${#jars[@]} under target"
  printf '%s\n' "${jars[0]}"
}

create_k8s_archive() {
  local archive

  [[ -d "${LOCAL_K8S_DIR}" ]] || die "Kubernetes manifest directory not found: ${LOCAL_K8S_DIR}"
  archive="$(mktemp "${TMPDIR:-/tmp}/tracking-k8s.XXXXXX")"
  tar -C "${LOCAL_K8S_DIR}" -czf "${archive}" .
  printf '%s\n' "${archive}"
}

build_k8s_apply_command() {
  local remote_archive="$1"

  printf 'mkdir -p %q && tar -xzf %q -C %q && rm -f %q && (kubectl get namespace %q >/dev/null 2>&1 || kubectl create namespace %q) && kubectl -n %q apply --recursive -f %q' \
    "${K8S_MANIFEST_DIR}" \
    "${remote_archive}" \
    "${K8S_MANIFEST_DIR}" \
    "${remote_archive}" \
    "${K8S_NAMESPACE}" \
    "${K8S_NAMESPACE}" \
    "${K8S_NAMESPACE}" \
    "${K8S_MANIFEST_DIR}"
}

build_k8s_preflight_command() {
  local unit command=""

  for unit in "${UNITS[@]}"; do
    command+="kubectl -n $(printf '%q' "${K8S_NAMESPACE}") get deployment/$(printf '%q' "tracking-${unit}") >/dev/null && "
    command+="kubectl -n $(printf '%q' "${K8S_NAMESPACE}") get service/$(printf '%q' "tracking-${unit}") >/dev/null && "
    command+="kubectl -n $(printf '%q' "${K8S_NAMESPACE}") get configmap/$(printf '%q' "tracking-${unit}-config") >/dev/null && "
    command+="kubectl -n $(printf '%q' "${K8S_NAMESPACE}") get pvc/$(printf '%q' "tracking-${unit}-pvc") >/dev/null && "
  done
  printf '%strue' "${command}"
}

build_k8s_rollout_command() {
  local unit command=""

  for unit in "${UNITS[@]}"; do
    command+="kubectl -n $(printf '%q' "${K8S_NAMESPACE}") rollout restart deployment/$(printf '%q' "tracking-${unit}") && "
  done
  for unit in "${UNITS[@]}"; do
    command+="kubectl -n $(printf '%q' "${K8S_NAMESPACE}") rollout status deployment/$(printf '%q' "tracking-${unit}") --timeout=$(printf '%q' "${K8S_ROLLOUT_TIMEOUT}") && "
  done
  printf '%strue' "${command}"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
[[ $# -eq 0 ]] || die "Unknown argument: $1"

require_command "${MAVEN_BIN}"
require_command find
require_command sshpass
require_command ssh
require_command scp
require_command tar
require_env NFS_SSH_PASS
require_env K8S_SSH_PASS

[[ -f "${LOCAL_DOCKER_DIR}/Dockerfile" ]] ||
  die "Dockerfile not found: ${LOCAL_DOCKER_DIR}/Dockerfile"
[[ -f "${LOCAL_DOCKER_DIR}/build-push.sh" ]] ||
  die "Build script not found: ${LOCAL_DOCKER_DIR}/build-push.sh"

log "Building shared tracking Jar"
build_jar

LOCAL_JAR_PATH="$(find_jar)"
LOCAL_JAR_NAME="${LOCAL_JAR_PATH##*/}"
REMOTE_UPLOAD_NAME="${LOCAL_JAR_NAME}.upload-${RUN_ID}"
REMOTE_BACKUP_NAME="${LOCAL_JAR_NAME}.bak-${RUN_ID}"

log "Deploying ${LOCAL_JAR_NAME} to ${NFS_DEPLOY_DIR}"
remote_bash "${NFS_SSH_PASS}" "${NFS_USER}" "${NFS_HOST}" "${NFS_PORT}" \
  "mkdir -p $(printf '%q' "${NFS_DEPLOY_DIR}")"
copy_to_remote "${NFS_SSH_PASS}" "${NFS_USER}" "${NFS_HOST}" "${NFS_PORT}" \
  "${LOCAL_DOCKER_DIR}/Dockerfile" "${NFS_DEPLOY_DIR}/Dockerfile"
copy_to_remote "${NFS_SSH_PASS}" "${NFS_USER}" "${NFS_HOST}" "${NFS_PORT}" \
  "${LOCAL_DOCKER_DIR}/build-push.sh" "${NFS_DEPLOY_DIR}/build-push.sh"
copy_to_remote "${NFS_SSH_PASS}" "${NFS_USER}" "${NFS_HOST}" "${NFS_PORT}" \
  "${LOCAL_JAR_PATH}" "${NFS_DEPLOY_DIR}/${REMOTE_UPLOAD_NAME}"
remote_bash "${NFS_SSH_PASS}" "${NFS_USER}" "${NFS_HOST}" "${NFS_PORT}" \
  "cd $(printf '%q' "${NFS_DEPLOY_DIR}") && if [[ -f $(printf '%q' "${LOCAL_JAR_NAME}") ]]; then mv $(printf '%q' "${LOCAL_JAR_NAME}") $(printf '%q' "${REMOTE_BACKUP_NAME}"); fi && mv $(printf '%q' "${REMOTE_UPLOAD_NAME}") $(printf '%q' "${LOCAL_JAR_NAME}") && bash $(printf '%q' "${NFS_BUILD_SCRIPT}")"

log "Synchronizing Kubernetes manifests to ${K8S_MANIFEST_DIR}"
LOCAL_K8S_ARCHIVE="$(create_k8s_archive)"
REMOTE_K8S_ARCHIVE="/tmp/tracking-k8s-${RUN_ID}.tar.gz"
trap 'rm -f "${LOCAL_K8S_ARCHIVE:-}"' EXIT
copy_to_remote "${K8S_SSH_PASS}" "${K8S_USER}" "${K8S_HOST}" "${K8S_PORT}" \
  "${LOCAL_K8S_ARCHIVE}" "${REMOTE_K8S_ARCHIVE}"
remote_bash "${K8S_SSH_PASS}" "${K8S_USER}" "${K8S_HOST}" "${K8S_PORT}" \
  "$(build_k8s_apply_command "${REMOTE_K8S_ARCHIVE}")"

log "Checking seven Deployments, Services, ConfigMaps, and PVCs"
remote_bash "${K8S_SSH_PASS}" "${K8S_USER}" "${K8S_HOST}" "${K8S_PORT}" \
  "$(build_k8s_preflight_command)"

log "Restarting seven tracking Deployments"
remote_bash "${K8S_SSH_PASS}" "${K8S_USER}" "${K8S_HOST}" "${K8S_PORT}" \
  "$(build_k8s_rollout_command)"

remote_bash "${NFS_SSH_PASS}" "${NFS_USER}" "${NFS_HOST}" "${NFS_PORT}" \
  "cd $(printf '%q' "${NFS_DEPLOY_DIR}") && rm -f $(printf '%q' "${REMOTE_BACKUP_NAME}")"

log "Deployment finished successfully"
