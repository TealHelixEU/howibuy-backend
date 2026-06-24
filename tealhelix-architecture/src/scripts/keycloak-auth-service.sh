#!/bin/bash

# Use this script to easily authenticate with client credentials to Keycloak. Example usage:
# > SVC_ACCESS_TOKEN=`./keycloak-auth-service.sh -u the_client -p secret`
# > curl -iS -X GET -H "Authorization: Bearer $SVC_ACCESS_TOKEN" -H "Accepts: text/html" http://localhost:8180/api/howibuy/v1/greeting
# Or:
# > curl -iS -X POST -H "Authorization: Bearer $SVC_ACCESS_TOKEN" -H "Content-Type: application/json" -d '{"correlationId": "abc"}' http://localhost:8180/api/howibuy/v1/tokenexchange
#
# This script requires curl, sed and jq.

KEYCLOAK_URL="http://localhost:8280"
REALM="tealhelix"
DECODED=n
PRINT_TOKEN_RESPONSE=n

decode() {
	jq -R 'split(".") | .[1] | @base64d | fromjson' <<< $1
}

while [[ $# -gt 0 ]]; do
	case $1 in
		-i|--idm)
			KEYCLOAK_URL="$2"
			shift
			shift
			;;
		-u|--client|--client-id)
			CLIENT_ID="$2"
			shift
			shift
			;;
		-p|--password)
			PASSWORD="$2"
			shift
			shift
			;;
		--decoded)
			DECODED=y
			shift
			;;
		--print-token-response)
			PRINT_TOKEN_RESPONSE=y
			shift
			;;
		*)
			echo "Unknown argument: $1"
			exit 2
			;;
	esac
done

if [[ -z "CLIENT_ID" ]]; then
	echo "Client ID (-u|--client|--client-id) is required"
	exit 1
fi
if [[ -z "$PASSWORD" ]]; then
	echo "Password/client secret (-p|--password) is required"
	exit 1
fi

TOKEN_RESPONSE=$(curl -sS -X POST \
	--data-urlencode "grant_type=client_credentials" \
	--data-urlencode "client_id=${CLIENT_ID}" \
	--data-urlencode "client_secret=${PASSWORD}" \
	"$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token")

if [[ "$PRINT_TOKEN_RESPONSE" = "y" ]]; then
	echo $TOKEN_RESPONSE | jq
	echo
fi

ACCESS_TOKEN=`echo $TOKEN_RESPONSE | jq -r ".access_token"`

echo $ACCESS_TOKEN

if [[ "$DECODED" = "y" ]]; then
	printf "\n\nDecoded Access Token:\n"
	decode $ACCESS_TOKEN
fi
