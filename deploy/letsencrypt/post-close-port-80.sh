#!/bin/sh
# T56.3 · certbot post-hook: close port 80 again (runs even if renewal failed).
systemctl start nginx
ufw delete allow 80/tcp
