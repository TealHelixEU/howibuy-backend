#!/bin/bash

# Use this script to easily run the OpenID Connect code flow from bash. Example usage:
# > ACCESS_TOKEN=`./keycloak-auth.sh -u bob@krusty-krab.com -p bob`
# > curl -iS -X GET -H "Authorization: Bearer $ACCESS_TOKEN" -H "Accepts: text/html" http://localhost:8180/api/howibuy/v1/greeting
# Requires curl, sed and jq.
# From https://medium.com/@rishabhsvats/understanding-authorization-code-flow-3946d746407

KEYCLOAK_URL="http://localhost:8280"
REDIRECT_URL="http://localhost:8180/howibuy/"
REALM="tealhelix"
CLIENTID="howibuy"
COOKIE="`pwd`/cookie.jar"
DECODED=n
PRINT_TOKEN_RESPONSE=n
DEBUG=
STOP_AT=1111
KEEP_COOKIE=

decode() {
	jq -R 'split(".") | .[1] | @base64d | fromjson' <<< $1
}

while [[ $# -gt 0 ]]; do
	case $1 in
		-u|--user|--username)
			USERNAME="$2"
			shift
			shift
			;;
		-p|--password)
			PASSWORD="$2"
			shift
			shift
			;;
		--cookie)
			COOKIE="$2"
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
		--debug)
			DEBUG=y
			shift
			;;
		--keep-cookie)
			KEEP_COOKIE=y
			shift
			;;
		--stop-at)
			STOP_AT="$2"
			DEBUG=y
			shift
			shift
			;;
		*)
			echo "Unknown argument: $1"
			exit 2
			;;
	esac
done

function cleanup {
	if [[ "$KEEP_COOKIE" != "y" ]]; then
		rm $COOKIE
	fi
}

if [[ -z "$USERNAME" ]]; then
	echo "Username (-u|--user|--username) is required"
	exit 1
fi
if [[ -z "$PASSWORD" ]]; then
	echo "Password (-p|--password) is required"
	exit 1
fi

AUTHENTICATE_URL=$(curl -sSL --get --cookie "$COOKIE" --cookie-jar "$COOKIE" \
	--data-urlencode "client_id=${CLIENTID}" \
	--data-urlencode "redirect_uri=${REDIRECT_URL}" \
	--data-urlencode "scope=openid" \
	--data-urlencode "response_type=code" \
	"$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/auth" \
	| sed -ne '/<form/s/^.*action=\"\([^"]*\)".*$/\1/p' | sed -e 's/\&amp;/\&/g')

[[ "$DEBUG" = "y" ]] && printf "AUTHENTICATE_URL:\n$AUTHENTICATE_URL\n"
[[ "$STOP_AT" -le 1 ]] && cleanup && exit 10

if [[ -z "AUTHENTICATE_URL" ]]; then
	echo "Something went wrong when retrieving the authentication URL"
	exit 3
fi

CODE_URL=$(curl -sS --cookie "$COOKIE" --cookie-jar "$COOKIE" \
	--data-urlencode "username=$USERNAME" \
	--data-urlencode "password=$PASSWORD" \
	--write-out "%{REDIRECT_URL}" \
	"$AUTHENTICATE_URL")

[[ "$DEBUG" = "y" ]] && printf "CODE_URL:\n$CODE_URL\n"
[[ "$STOP_AT" -le 2 ]] && cleanup && exit 10

CODE=`echo $CODE_URL | sed -e "s/^.*[&?]code=//" -e "s/&.*$//"`

[[ "$DEBUG" = "y" ]] && printf "CODE:\n$CODE\n"
[[ "$STOP_AT" -le 3 ]] && cleanup && exit 10

TOKEN_RESPONSE=$(curl -sS --cookie "$COOKIE" --cookie-jar "$COOKIE" \
	--data-urlencode "client_id=$CLIENTID" \
	--data-urlencode "redirect_uri=$REDIRECT_URL" \
	--data-urlencode "code=$CODE" \
	--data-urlencode "grant_type=authorization_code" \
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

cleanup
