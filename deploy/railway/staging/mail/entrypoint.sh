#!/bin/sh
set -eu
umask 077
: "${STAGING_SMTP_CERT_PEM:?staging TLS certificate required}"
: "${STAGING_SMTP_KEY_PEM:?staging TLS key required}"
: "${MP_SMTP_AUTH:?staging SMTP authentication required}"
: "${MP_UI_AUTH:?staging mail API authentication required}"
printf '%s\n' "$STAGING_SMTP_CERT_PEM" > /tmp/staging-smtp.crt
printf '%s\n' "$STAGING_SMTP_KEY_PEM" > /tmp/staging-smtp.key
unset STAGING_SMTP_KEY_PEM STAGING_SMTP_CERT_PEM
export MP_SMTP_TLS_CERT=/tmp/staging-smtp.crt
export MP_SMTP_TLS_KEY=/tmp/staging-smtp.key
export MP_SMTP_REQUIRE_STARTTLS=true
export MP_SMTP_AUTH_ALLOW_INSECURE=false
export MP_SMTP_AUTH_ACCEPT_ANY=false
export MP_SMTP_ALLOWED_RECIPIENTS='@staging\.invalid$'
export MP_SMTP_BIND_ADDR='[::]:1025'
export MP_UI_BIND_ADDR='[::]:8025'
export MP_SMTP_DISABLE_RDNS=true
export MP_DISABLE_VERSION_CHECK=true
export MP_MAX_MESSAGES=100
# Forwarding/relay must never be configured on this capture-only service.
if env | cut -d= -f1 | grep -Eq '^MP_(SMTP_(FORWARD|RELAY)|WEBHOOK)'; then
    echo 'Staging mail must not forward, relay or call webhooks.' >&2
    exit 1
fi
exec /mailpit
