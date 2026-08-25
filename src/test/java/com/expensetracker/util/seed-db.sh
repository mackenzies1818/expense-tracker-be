#!/usr/bin/env bash
#
# seed-via-api.sh
#
# Seeds 3 test users and 10-15 expenses each by calling the running app's
# real REST API — /api/auth/register and /api/expenses — rather than
# touching the database directly. This exercises the actual app code
# (password hashing, validation, exception handling) instead of bypassing it.
#
# Usage:
#   ./seed-via-api.sh                                # defaults to http://localhost:8080/api
#   ./seed-via-api.sh http://localhost:8080/api       # explicit base URL
#
# Requires: curl, jq
# Requires: the app already running (e.g. `docker compose up -d` or `mvn spring-boot:run`)

set -euo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
SEED_PASSWORD="Password123!"

EMAILS=(alice1@example.com bob1@example.com carol1@example.com)

CATEGORIES=(GROCERIES HOUSING EATING_OUT TOILETRIES FUN TRANSPORTATION MISC)

for cmd in curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Error: '$cmd' is required but not installed."
        exit 1
    fi
done

if ! curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/auth/login" -X POST \
        -H "Content-Type: application/json" -d '{}' | grep -qE '^[0-9]{3}$'; then
    echo "Error: could not reach ${BASE_URL}. Is the app running?"
    exit 1
fi

description_for_category() {
    case "$1" in
        GROCERIES)      echo -e "Weekly grocery run\nCostco haul\nFarmers market produce" ;;
        HOUSING)        echo -e "Monthly rent\nElectricity bill\nWater bill\nInternet bill" ;;
        EATING_OUT)     echo -e "Dinner with friends\nCoffee shop\nSushi lunch\nPizza night" ;;
        TOILETRIES)     echo -e "Shampoo and soap\nToothpaste\nPharmacy run" ;;
        FUN)            echo -e "Movie tickets\nConcert tickets\nStreaming subscription\nBowling night" ;;
        TRANSPORTATION) echo -e "Gas fill-up\nMonthly transit pass\nRideshare\nParking fee" ;;
        MISC)           echo -e "Birthday gift\nOffice supplies\nDonation" ;;
    esac
}

amount_range_for_category() {
    case "$1" in
        HOUSING)        echo "200 1800" ;;
        TRANSPORTATION) echo "15 200" ;;
        GROCERIES)      echo "10 150" ;;
        EATING_OUT)     echo "8 90" ;;
        FUN)            echo "10 120" ;;
        TOILETRIES)     echo "5 40" ;;
        *)              echo "5 100" ;; # MISC
    esac
}

random_element() {
    # picks a random element from the args passed in — call as:
    #   random_element "${SOME_ARRAY[@]}"
    # Avoids bash namerefs (`local -n`), which require bash >=4.3 and are
    # unsupported by the bash 3.2 that macOS ships by default.
    local elements=("$@")
    echo "${elements[$((RANDOM % ${#elements[@]}))]}"
}

random_amount() {
    local min="$1" max="$2"
    awk -v min="$min" -v max="$max" -v seed="$RANDOM$$" \
        'BEGIN { srand(seed); printf "%.2f", min + rand() * (max - min) }'
}

random_line() {
    local input="$1"
    local lines=()

    while IFS= read -r line; do
        lines+=("$line")
    done <<< "$input"

    echo "${lines[$((RANDOM % ${#lines[@]}))]}"
}

register_or_login() {
    local email="$1"
    local response status accessToken

    response=$(curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${email}\",\"password\":\"${SEED_PASSWORD}\"}")
    status=$(tail -n1 <<< "$response")
    body=$(sed '$d' <<< "$response")

    if [ "$status" = "200" ]; then
        echo "$body" | jq -r '.accessToken'
        return
    fi

    # Already registered (409) — fall back to logging in to get a usable token
    echo "  '${email}' already exists (status ${status}) — logging in instead..." >&2
    response=$(curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${email}\",\"password\":\"${SEED_PASSWORD}\"}")
    status=$(tail -n1 <<< "$response")
    body=$(sed '$d' <<< "$response")

    if [ "$status" != "200" ]; then
        echo "  Failed to register or log in '${email}' (status ${status}): ${body}" >&2
        return 1
    fi

    echo "$body" | jq -r '.accessToken'
}

for email in "${EMAILS[@]}"; do
    echo "Seeding ${email}..."

    if ! accessToken=$(register_or_login "$email"); then
        echo "  Skipping ${email} due to auth failure."
        continue
    fi

    expense_count=$((10 + RANDOM % 6)) # 10-15 inclusive
    created=0

    for ((i = 0; i < expense_count; i++)); do
        category="${CATEGORIES[$((RANDOM % ${#CATEGORIES[@]}))]}"
        description=$(random_line "$(description_for_category "$category")")
        read -r min max <<< "$(amount_range_for_category "$category")"
        amount=$(random_amount "$min" "$max")

        status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BASE_URL}/expenses" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${accessToken}" \
            -d "{\"description\":\"${description}\",\"amount\":${amount},\"category\":\"${category}\",\"expenseToken\":null}")

        if [ "$status" = "201" ]; then
            created=$((created + 1))
        else
            echo "  Warning: expense creation failed (status ${status})" >&2
        fi
    done

    echo "  Created ${created}/${expense_count} expenses for ${email}."
done

echo ""
echo "Done. Log in with any of:"
for email in "${EMAILS[@]}"; do
    echo "  ${email} / ${SEED_PASSWORD}"
done