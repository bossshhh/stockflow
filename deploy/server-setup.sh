#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 server, run as root:
#   ./server-setup.sh            Docker, firewall, automatic security updates, a "deploy" user (SSH key only)
#   ./server-setup.sh --lock-ssh AFTER you have logged in as "deploy": no passwords, no root login over SSH
# Two phases on purpose: locking SSH before the new user's login is proven could lock everyone out.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Run as root" >&2; exit 1; }

if [[ "${1:-}" == "--lock-ssh" ]]; then
  cat > /etc/ssh/sshd_config.d/99-stockflow.conf <<'CONF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
CONF
  sshd -t && systemctl reload ssh
  echo "SSH locked: key-only, no root login."
  exit 0
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update && apt-get -y upgrade
apt-get -y install ca-certificates curl jq ufw unattended-upgrades

# Docker from Docker's own repository (the distribution's package lags behind).
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update && apt-get -y install docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Security updates install themselves.
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'CONF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
CONF

# Firewall: SSH, HTTP (certificate challenge + redirect) and HTTPS (TCP, and UDP for HTTP/3).
# Note: Docker bypasses ufw for PUBLISHED ports, which is why the compose file publishes only Caddy's.
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443
ufw --force enable

# The account that runs the app. Being in the docker group is root-equivalent; acceptable on a server that
# exists only for this app, and it keeps day-to-day work off the root account.
id deploy &>/dev/null || adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -m 600 -o deploy -g deploy /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
install -d -o deploy -g deploy /opt/stockflow

echo "Done. Now check you can log in:  ssh deploy@<server>   then run:  ./server-setup.sh --lock-ssh"
