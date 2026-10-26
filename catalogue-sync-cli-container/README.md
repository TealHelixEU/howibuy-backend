# Catalogue Sync

## A Feed through the whole pipeline

A Retailer's Feed goes to the Ingestion, and the Catalogue Sync carries the Product Changes into HowiBuy. To run it
locally, start the peripherals (see [Docker compose](../README.md#docker-compose)), then each service in a terminal of
its own. Run all the commands from the project root. In dev mode both services seed the development Retailer and its
Catalogue, `lime-fresh-gr`:

```shell
mvn package -Pcatalogue-quarkus-dev,th-local-postgres -DskipTests
mvn package -Phowibuy-quarkus-dev,th-local-postgres -DskipTests
```

Push a Snapshot of the Catalogue as the Retailer, with the access token of its client, `lime_fresh`. The end-to-end
tests have fixture Feeds to push:

```shell
TOKEN=$(curl -s http://localhost:8280/realms/tealhelix/protocol/openid-connect/token \
	-d grant_type=client_credentials -d client_id=lime_fresh -d client_secret=GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1 \
	| jq -r .access_token)
curl -i -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/x-ndjson" \
	--data-binary @catalogue-sync-cli-container/catalogue-sync-e2e/src/test/resources/feeds/lime-fresh-gr-snapshot-1.jsonl \
	"http://localhost:8380/api/catalogue/v1/catalogues/lime-fresh-gr/feeds?type=SNAPSHOT"
```

The Ingestion answers `202` and processes the Feed in the background; the `Location` header is its Ingestion Run,
whose `status` becomes `DONE`:

```shell
curl -H "Authorization: Bearer $TOKEN" <the Location>
```

Then build the Catalogue Sync and run its `sync` command, with the secret of its client, `catalogue-sync`. It brings
HowiBuy up to date with the Ingestion and prints the pages, the products and the cursor it reached:

```shell
mvn package -pl catalogue-sync-cli-container/catalogue-sync-cli -am -DskipTests
java -Dquarkus.oidc-client.credentials.secret=7poZpd5n2Abtu7LKcB0MoAWLHBUnhQzU \
	-jar catalogue-sync-cli-container/catalogue-sync-cli/target/quarkus-app/quarkus-run.jar sync
```

It finds both services and the IDM at their local ports; `quarkus.rest-client.ingestion.url`,
`quarkus.rest-client.howibuy.url`, `quarkus.oidc-client.auth-server-url` and `catalogue-sync.page-size` change that.
Push `lime-fresh-gr-snapshot-2.jsonl` and sync again to see the oranges go off sale and the milk cost more.

## Classifying the pending products

The `classify` command has HowiBuy's LLM classify the products waiting to be classified, one attempt per language a
product is named in. It needs Ollama, at HowiBuy's `quarkus.langchain4j.ollama.productAssessment.base-url`. It prints
the products and attempts it made, their outcomes, the attempts that failed and the time it took:

```shell
java -Dquarkus.oidc-client.credentials.secret=7poZpd5n2Abtu7LKcB0MoAWLHBUnhQzU \
	-jar catalogue-sync-cli-container/catalogue-sync-cli/target/quarkus-app/quarkus-run.jar classify --limit 10 --rate 30
```

`--limit` counts products, by default `catalogue-sync.classification.limit`; `--rate` counts the attempts started in a
minute, by default `catalogue-sync.classification.rate`, and is unthrottled when neither is set. A failed attempt is
counted and skipped, and the run goes on; running it again goes on with the products still pending.
`catalogue-sync.classification.page-size` sets how many pending products it reads from HowiBuy at a time.

After the classifier improves, an operator reopens the Classifications that came from its earlier version, which makes
their products pending again, and `classify` classifies them again. The answer tells how many were reopened:

```shell
TOKEN=$(tealhelix-architecture/src/scripts/keycloak-auth.sh -u bob@krusty-krab.com -p bob)
curl -i -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
	-d '{"classifier": "LLM", "classifierVersion": "llama3.1:8b/prompt-1/glossary-1"}' \
	http://localhost:8180/api/howibuy/v1/classifications/reopening
```
