#!/bin/bash

# Use this script to create a handoff ticket - the ticket that gives access to the howibuy-front directly from a retailer.
# First authenticate as service and obtain the access token:
# > SVC_ACCESS_TOKEN=`./keycloak-auth-service.sh -u the_client -p secret`
# Call this script to print the ticket:
# > ./handover.sh -c ABCD -a $SVC_ACCESS_TOKEN`
# The previous command has printed the ticket; use it to direct the browser to:
# http://localhost:5175/#ticket=<the-ticket>
#
# This script requires curl and jq

HOWIBUY_URL="http://localhost:8180/api/howibuy/v1"
CORRELATION_ID=
FRONTEND_URL=

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
		--print-ticket-response)
			PRINT_TICKET_RESPONSE=y
			shift
			;;
		-f|--frontend-url)
			FRONTEND_URL="$2"
			shift
			shift
			;;
		*)
			echo "Unknown argument: $1"
			exit 2
			;;
	esac
done

TICKET_RESPONSE=$(curl -s -X POST $HOWIBUY_URL/handoff \
	-d "{ \"correlationId\": \"$CORRELATION_ID\" }" \
	-H "Authorization: Bearer $ACCESS_TOKEN" \
	-H "Content-Type: application/json")

if [[ "$PRINT_TICKET_RESPONSE" = "y" ]]; then
	echo $TICKET_RESPONSE
	echo
fi

TICKET=`echo $TICKET_RESPONSE | jq -r ".ticket"`

if [[ -z "$FRONTEND_URL" ]]; then
	echo $TICKET
else
	echo "$FRONTEND_URL#ticket=$TICKET"
fi
