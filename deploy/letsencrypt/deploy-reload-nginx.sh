#!/bin/sh
# T56.3 · certbot deploy-hook: load the renewed certificate.
systemctl reload nginx
