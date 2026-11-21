#!/bin/sh
# T56.3 · certbot pre-hook: open port 80 only while the certificate is issued/renewed.
ufw allow 80/tcp
systemctl stop nginx
