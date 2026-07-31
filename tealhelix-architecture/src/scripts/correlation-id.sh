#!/bin/bash

# Use this script to run the various correlation id flows. First authenticate as service and obtain the access token:
# > SVC_ACCESS_TOKEN=`./keycloak-auth-service.sh -u the_client -p secret`
# > IMPERSONATION_TOKEN=`./correlation-id.sh -c ABCD -a $SVC_ACCESS_TOKEN`
# You can then access the system on behalf of the user with the given correlation id, e.g.:
# > curl -iS -X GET -H "Authorization: Bearer $IMPERSONATION_TOKEN" -H "Accepts: text/html" http://localhost:8180/api/howibuy/v1/greeting
#
# This script requires curl and jq

HOWIBUY_URL="http://localhost:8180/api/howibuy/v1"
CORRELATION_ID=

while [[ $# -gt 0 ]]; do
	case $1 in
		-s|--server-url)
			HOWIBUY_URL="$2"
			shift
			shift
			;;
		-a|--access-token)
			ACCESS_TOKEN="$2"
			shift
			shift
			;;
		-c|--correlation-id)
			CORRELATION_ID="$2"
			shift
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

TOKEN_RESPONSE=$(curl -s -X POST $HOWIBUY_URL/tokenexchange \
	-d "{ \"correlationId\": \"$CORRELATION_ID\" }" \
	-H "Authorization: Bearer $ACCESS_TOKEN" \
	-H "Content-Type: application/json")

if [[ "$PRINT_TOKEN_RESPONSE" = "y" ]]; then
	echo $TOKEN_RESPONSE
	echo
fi

IMPERSONATION_TOKEN=`echo $TOKEN_RESPONSE | jq -r ".access_token"`

echo $IMPERSONATION_TOKEN
