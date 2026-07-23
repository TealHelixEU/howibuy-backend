# TealHelix

TealHelix aims to empower consumers with personalised and inclusive labelling solutions that promote sustainable
food choices for everyone.

This project is part of the ICT solutions of TealHelix.

## The build system

The build system is Maven and is configured by a set of properties and profiles, as follows:

### Build properties

The following properties are local to an environment; they can be specified as `-Dpropname=propvalue` command line arguments,
or placed in a local Maven profile in `~/.m2/settings.xml`.

- `database.howibuy.jdbc.url`: The JDBC URL of the database for the respective microservice
- `database.howibuy.reactive.url`: The Hibernate *reactive* URL of the database for the respective microservice
- `database.howibuy.username`: The DB username
- `database.howibuy.password`: The DB password
- **(TODO)** `db.env` (default: `dev`): Needed only by Liquibase to indicate which environment-specific
  [contexts](https://www.liquibase.org/documentation/contexts.html) will it activate;
  e.g. `dev` will activate the `data-dev` context

- Example:

```xml
<settings>
	<profiles>
		<profile>
			<id>th-local-postgres</id>
			<properties>
				<database.howibuy.jdbc.url>jdbc:postgresql://localhost/tealhelix</database.howibuy.jdbc.url>
				<database.howibuy.reactive.url>vertx-reactive:postgresql://localhost/tealhelix</database.howibuy.reactive.url>
				<database.howibuy.username>th_howibuy</database.howibuy.username>
				<database.howibuy.password>th_howibuy</database.howibuy.password>
			</properties>
		</profile>
		<profile>
			<id>th-docker-postgres</id>
			<properties>
				<database.howibuy.jdbc.url>jdbc:postgresql://postgres/tealhelix</database.howibuy.jdbc.url>
				<database.howibuy.reactive.url>vertx-reactive:postgresql://postgres/tealhelix</database.howibuy.reactive.url>
				<database.howibuy.username>th_howibuy</database.howibuy.username>
				<database.howibuy.password>th_howibuy</database.howibuy.password>
			</properties>
		</profile>
	</profiles>
</settings>
```

Both profiles use Postgresql. One (`th-docker-postgres`) is to run the entire application through `docker compose`, in which case
Postgresql is in the `postgres` host - see `tealhelix-docker/src/main/docker-compose/docker-compose.yml` (**TODO**).
The other (`th-local-postgres`) is to run only the peripherals in Docker - see `tealhelix-docker/src/main/docker-compose/docker-compose-peripherals.yml`.

### Build profiles

- `dbupdate-howibuy`: Execute Liquibase to bring the respective database up to date; you will need to specify connection data, e.g. using the profiles above
- `howibuy-quarkus-dev`: Activate `quarkus:dev` for the respective service; do not activate more than one in the same command
- `docker`: Activate the Docker image build

### Updating dependencies

The versions of all dependencies are controlled by Maven properties in the form `version.<uniqueId>`,
where `<uniqueId>` is a unique identifier for the dependency, preferably the artifact id, but anything
unique and sufficiently descriptive will do. All version properties are defined in the parent pom.
As such, detecting updates is as simple as running (`-N` for non-recursive build, since all version properties are
in the parent pom):

```shell
mvn -N versions:display-property-updates
mvn -N versions:display-plugin-updates
```

Some versions are affected by the requirements of Quarkus. We want our artifacts to be as environment-independent as
possible therefore, we do not use Quarkus dependencies anywhere, except from the deployment modules.
However, we need to stay compatible; so we mark version properties that actually depend on Quarkus with an XML comment.
Be careful when upgrading those dependencies.

## Building

Build with Maven as usual, `package` is enough:

```shell
mvn package
# -OR-
mvn package -Pdocker # to build the docker images too
```

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

### Creating/updating the DB

The aggregating changelog root now lives in the `howibuy` deployable and `<include>`s per-module changelogs that
ship inside the DAO-impl artifacts, so Liquibase resolves them from the classpath. Build/install the reactor first
(`mvn install`) so those artifacts are on the plugin classpath.

Assuming that the properties are defined through a Maven profile e.g., like the `th-local-postgres` in
`~/.m2/settings.xml` that was described above, run the following:

```shell
mvn process-resources -Pdbupdate-howibuy,th-local-postgres
```

The `dev` context add data for the development environment; add `-Dliquibase.contexts=dev` to the previous command to activate.

Otherwise, you have to specify the properties by command line, a much more cumbersome option:

```shell
mvn process-resources -Pdbupdate-howibuy -Ddatabase.howibuy.jdbc.url=... -Ddatabase.howibuy.username=... -Ddatabase.howibuy.password=... -D...
```

#### Rolling back changes

Occasionally you may want to roll back some changes. The aggregating changelog root lives in the `howibuy` deployable
module (`howibuy-container/howibuy`); its per-module changelogs live in the DAO-impl modules, so build/install the
modules first (`mvn install`) for them to resolve. Switch to the deployable module and run
(example is for HowiBuy):

```shell
mvn org.liquibase:liquibase-maven-plugin:rollback \
	-Dliquibase.rollbackCount=... -Dliquibase.changeLogFile=src/main/resources/db.changelog.xml \
	-Dliquibase.promptOnNonLocalDatabase=false -Dliquibase.driver=org.postgresql.Driver \
	-Dliquibase.url=jdbc:postgresql://localhost/tealhelix -Dliquibase.username=th_howibuy \
	-Dliquibase.password=th_howibuy
```

Full info [here](https://docs.liquibase.com/tools-integrations/maven/commands/maven-rollback.html).

## Running

### Docker compose

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

This will be the first thing you need to run in a local development environment, as it launches all the
necessary peripheral services (e.g., the database).

```shell
cd tealhelix-docker/src/main/docker-compose/
docker compose -f docker-compose-peripherals.yml -p tealhelix up -d    # the first time
docker compose -f docker-compose-peripherals.yml -p tealhelix start    # to start
docker compose -f docker-compose-peripherals.yml -p tealhelix stop     # to stop
docker compose -f docker-compose-peripherals.yml -p tealhelix down     # to remove the containers, without removing the persistent volumes
docker compose -f docker-compose-peripherals.yml -p tealhelix down -v  # to remove the containers, also removing the persistent volumes
```

### From the command line with Maven

From the project root, activate the profile of the microservice you want to run (don't forget the profile with the DB settings):

```shell
mvn package -Phowibuy-quarkus-dev,th-local-postgres -DskipTests
```

### From IDE, specifying the environment variables

Create a Quarkus run configuration. You need to specify the DB connection parameters (and any other runtime parameters)
from the run configuration. Select "Modify options" and check "Environment variables." Override the following
configuration properties:

- `quarkus.datasource.username`
- `quarkus.datasource.password`
- `quarkus.datasource.reactive.url`
- `quarkus.datasource.jdbc.url`

The table would look like (see [Quarkus configuration reference](https://quarkus.io/guides/config-reference)):

| Name                            | Value                                           |
|---------------------------------|-------------------------------------------------|
| QUARKUS_DATASOURCE_USERNAME     | th_howibuy                                      |
| QUARKUS_DATASOURCE_PASSWORD     | th_howibuy                                      |
| QUARKUS_DATASOURCE_REACTIVE_URL | vertx-reactive:postgresql://localhost/tealhelix |
| QUARKUS_DATASOURCE_JDBC_URL     | jdbc:postgresql://localhost/tealhelix           |

Or define them inline using semicolon as the separator:
`QUARKUS_DATASOURCE_USERNAME=th_howibuy;QUARKUS_DATASOURCE_PASSWORD=th_howibuy;QUARKUS_DATASOURCE_REACTIVE_URL=vertx-reactive:postgresql://localhost/tealhelix;QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost/tealhelix`

You need to make sure the IDE runner resolves workspace artifacts.

### From IDE, specifying the Maven profile

Instead of specifying all the environment variables, you can use the Maven profile you have created in `~/.m2/settings.xml`.
Create a Quarkus run configuration. Select "Modify options" and then "Quarkus" -> "Add arguments". The value of the
argument should be `-Pth-local-postgres`.

### From Docker

**TODO**


## Releases

```bash
git checkout master
NEW_VERSION=x.y.z
# merge appropriately
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=$NEW_VERSION
git commit -am "Version $NEW_VERSION"
git tag -am "Version $NEW_VERSION" v$NEW_VERSION
git checkout develop
git merge master
mvn versions:set -DnewVersion=1.0.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Continue development with 1.0.0-SNAPSHOT"
git push --tags origin master:master develop:develop
```
