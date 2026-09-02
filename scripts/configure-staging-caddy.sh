#!/usr/bin/env bash
set -Eeuo pipefail

API_HOSTNAME="${1:-}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root on the staging VPS." >&2
  exit 1
fi

if [[ ! "$API_HOSTNAME" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || [[ "$API_HOSTNAME" != *.* ]]; then
  echo "Provide a valid lowercase public hostname, for example api.203-0-113-10.sslip.io." >&2
  exit 1
fi

if ! command -v caddy >/dev/null 2>&1; then
  echo "Caddy is not installed. Run install-staging-host.sh first." >&2
  exit 1
fi

TEMP_CADDYFILE="$(mktemp)"
trap 'rm -f "$TEMP_CADDYFILE"' EXIT

cat > "$TEMP_CADDYFILE" <<EOF
$API_HOSTNAME {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8090

    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "strict-origin-when-cross-origin"
        -Server
    }

    log {
        output file /var/log/caddy/viet-kham-pha-api.log {
            roll_size 10MiB
            roll_keep 5
            roll_keep_for 168h
        }
        format json
    }
}
EOF

caddy fmt --overwrite "$TEMP_CADDYFILE"
caddy validate --config "$TEMP_CADDYFILE" --adapter caddyfile

if [[ -f /etc/caddy/Caddyfile ]]; then
  cp /etc/caddy/Caddyfile "/etc/caddy/Caddyfile.backup-$(date -u +%Y%m%dT%H%M%SZ)"
fi
install -m 0644 "$TEMP_CADDYFILE" /etc/caddy/Caddyfile
systemctl reload caddy

echo "Caddy now serves https://$API_HOSTNAME and proxies only to the loopback Gateway."
