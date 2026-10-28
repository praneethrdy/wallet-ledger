#!/usr/bin/env bash
# Sends a correctly signed payment-provider webhook to a running backend.
#
#   scripts/sign-webhook.sh <providerReference> <amountMinor> [succeeded|failed] [currency]
#
# Example (after POST /api/v1/deposits returned providerReference=mock_abc and amount=50000):
#   scripts/sign-webhook.sh mock_abc 50000
#
# Signature = hex(HMAC-SHA256(secret, "<timestamp>.<raw body>")), sent as X-Webhook-Signature,
# with the same <timestamp> in X-Webhook-Timestamp. The body is sent byte-for-byte as signed.
#
# Env: WEBHOOK_SECRET (default: the dev secret), WEBHOOK_URL (default: local backend).
set -euo pipefail

REF=${1:?usage: sign-webhook.sh <providerReference> <amountMinor> [succeeded|failed] [currency]}
AMOUNT=${2:?amountMinor required (e.g. 50000 for INR 500.00)}
OUTCOME=${3:-succeeded}
CURRENCY=${4:-INR}
SECRET=${WEBHOOK_SECRET:-dev-webhook-secret-change-me}
URL=${WEBHOOK_URL:-http://localhost:8080/api/v1/webhooks/payment-provider}

EVENT_ID="evt_$(date +%s)_${RANDOM}${RANDOM}"
BODY=$(printf '{"id":"%s","type":"deposit.%s","data":{"providerReference":"%s","amount":%s,"currency":"%s"}}' \
  "$EVENT_ID" "$OUTCOME" "$REF" "$AMOUNT" "$CURRENCY")
TS=$(date +%s)
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')

echo "POST $URL  (event $EVENT_ID)" >&2
curl -sS -X POST "$URL" \
  -H 'Content-Type: application/json' \
  -H "X-Webhook-Timestamp: $TS" \
  -H "X-Webhook-Signature: $SIG" \
  --data-binary "$BODY"
echo
