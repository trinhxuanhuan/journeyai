#!/usr/bin/env bash
set -Eeuo pipefail

SSH_PORT="${1:-22}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root on a fresh Ubuntu or Debian VPS." >&2
  exit 1
fi

if [[ ! "$SSH_PORT" =~ ^[0-9]{1,5}$ ]] || (( SSH_PORT < 1 || SSH_PORT > 65535 )); then
  echo "SSH port must be an integer between 1 and 65535." >&2
  exit 1
fi

source /etc/os-release
if [[ "$ID" != "ubuntu" && "$ID" != "debian" ]]; then
  echo "Only Ubuntu and Debian are supported by this bootstrap script." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git gnupg ufw

install -m 0755 -d /etc/apt/keyrings
curl -fsSL "https://download.docker.com/linux/${ID}/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
ARCH="$(dpkg --print-architecture)"
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/%s %s stable\n' \
  "$ARCH" "$ID" "$VERSION_CODENAME" > /etc/apt/sources.list.d/docker.list

curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key \
  | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
  > /etc/apt/sources.list.d/caddy-stable.list

MICROSOFT_REPO_PACKAGE="$(mktemp --suffix=.deb)"
trap 'rm -f "$MICROSOFT_REPO_PACKAGE"' EXIT
curl -fsSL "https://packages.microsoft.com/config/${ID}/${VERSION_ID}/packages-microsoft-prod.deb" \
  -o "$MICROSOFT_REPO_PACKAGE"
dpkg -i "$MICROSOFT_REPO_PACKAGE"
rm -f "$MICROSOFT_REPO_PACKAGE"
trap - EXIT

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin caddy powershell
systemctl enable --now docker caddy

ufw default deny incoming
ufw default allow outgoing
ufw allow "$SSH_PORT/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

install -d -m 0750 /opt/viet-kham-pha

TOTAL_MEMORY_KB="$(awk '/MemTotal/ { print $2 }' /proc/meminfo)"
if (( TOTAL_MEMORY_KB < 15000000 )) && [[ "$(swapon --show --noheadings | wc -l)" -eq 0 ]]; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "Host bootstrap complete. Docker, Caddy, Git and PowerShell are ready; only SSH, HTTP and HTTPS are allowed inbound."
