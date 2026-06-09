# TealHelix

TealHelix aims to empower consumers with personalised and inclusive labelling solutions that promote sustainable
food choices for everyone.

This project is part of the ICT solutions of TealHelix.

## The build system

The build system is Maven and is configured by a set of properties and profiles, as follows:

### Build properties

The following properties are local to an environment; they can be specified as `-Dpropname=propvalue` command line arguments,
or placed in a local Maven profile in `~/.m2/settings.xml`.

- `database.betterme.jdbc.url`: The JDBC URL of the database for the respective microservice
- `database.betterme.reactive.url`: The Hibernate *reactive* URL of the database for the respective microservice
- `database.betterme.username`: The DB username
- `database.betterme.password`: The DB password
- **(TODO)** `kafka.bootstrap.servers`: The Kafka bootstrap servers
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
				<database.betterme.jdbc.url>jdbc:postgresql://localhost/tealhelix</database.betterme.jdbc.url>
				<database.betterme.reactive.url>vertx-reactive:postgresql://localhost/tealhelix</database.betterme.reactive.url>
				<database.betterme.username>th_betterme</database.betterme.username>
				<database.betterme.password>th_betterme</database.betterme.password>
				<kafka.bootstrap.servers>localhost:9092</kafka.bootstrap.servers>
			</properties>
		</profile>
		<profile>
			<id>th-docker-postgres</id>
			<properties>
				<database.betterme.jdbc.url>jdbc:postgresql://postgres/tealhelix</database.betterme.jdbc.url>
				<database.betterme.reactive.url>vertx-reactive:postgresql://postgres/tealhelix</database.betterme.reactive.url>
				<database.betterme.username>th_betterme</database.betterme.username>
				<database.betterme.password>th_betterme</database.betterme.password>
				<kafka.bootstrap.servers>broker:19092</kafka.bootstrap.servers>
			</properties>
		</profile>
	</profiles>
</settings>
```

Both profiles use Postgresql. One is to run the entire application through `docker-compose`, in which case
Postgresql is in the `postgres` host - see `tealhelix-docker/src/main/docker-compose/docker-compose.yml` (**TODO**).
The other is to run only the peripherals in Docker - see `tealhelix-docker/src/main/docker-compose/docker-compose-peripherals.yml`.

### Build profiles

- `dbupdate-betterme`: Execute Liquibase to bring the respective database up to date
- `betterme-quarkus-dev`: Activate `quarkus:dev` for the respective microservice; do not activate more than one in the same command
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

Assuming that the properties are defined through a Maven profile e.g., like the `th-local-postgres` in
`~/.m2/settings.xml` that was described above, run the following:

```shell
mvn process-resources -Pdbupdate-betterme,th-local-postgres
```

The `dev` context add data for the development environment; add `-Dliquibase.contexts=dev` to the previous command to activate.

Otherwise, you have to specify the properties by command line, a much more cumbersome option:

```shell
mvn process-resources -Pdbupdate-betterme -Ddatabase.betterme.jdbc.url=... -Ddatabase.betterme.username=... -Ddatabase.betterme.password=... -D...
```

#### Rolling back changes

Occasionally you may want to roll back some changes. Switch to the appropriate migration project and run
(example is for BetterMe/HowiBuy):

```shell
mvn org.liquibase:liquibase-maven-plugin:rollback \
	-Dliquibase.rollbackCount=... -Dliquibase.changeLogFile=src/main/resources/db.changelog.xml \
	-Dliquibase.promptOnNonLocalDatabase=false -Dliquibase.driver=org.postgresql.Driver \
	-Dliquibase.url=jdbc:postgresql://localhost/tealhelix -Dliquibase.username=th_betterme \
	-Dliquibase.password=th_betterme
```

Full info [here](https://docs.liquibase.com/tools-integrations/maven/commands/maven-rollback.html).

## Running

### Docker compose

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

This will be the first thing you need to run in a local development environment, as it launches all the
necessary peripheral services (e.g., the database).

```shell
cd tealhelix-docker/src/main/docker-compose/
docker-compose -f docker-compose-peripherals.yml -p tealhelix up -d    # the first time
docker-compose -f docker-compose-peripherals.yml -p tealhelix start    # to start
docker-compose -f docker-compose-peripherals.yml -p tealhelix stop     # to stop
docker-compose -f docker-compose-peripherals.yml -p tealhelix down     # to remove the containers, without removing the persistent volumes
docker-compose -f docker-compose-peripherals.yml -p tealhelix down -v  # to remove the containers, also removing the persistent volumes
```

### From the command line (TODO - has stopped working)

You need to `mvn install`, so that `quarkus:dev` will find the artifacts!

Cd to a Quarkus module and execute the `quarkus:dev` Maven goal. Don't forget to activate the profile that defines DB connection parameters. E.g.:

```shell
cd tealhelix-application-module/tealhelix-application
mvn -Ptealhelix-local-postgres quarkus:dev
```

### From the command line (2) (TODO)

From the project root, activate the profile of the microservice you want to run (don't forget the profile with the DB settings):

```shell
mvn package -Papplication-quarkus-dev,tealhelix-local-postgres
```

### From IDE

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
| QUARKUS_DATASOURCE_USERNAME     | th_betterme                                     |
| QUARKUS_DATASOURCE_PASSWORD     | th_betterme                                     |
| QUARKUS_DATASOURCE_REACTIVE_URL | vertx-reactive:postgresql://localhost/tealhelix |
| QUARKUS_DATASOURCE_JDBC_URL     | jdbc:postgresql://localhost/tealhelix           |

Or define them inline using semicolon as the separator:
`QUARKUS_DATASOURCE_USERNAME=th_betterme;QUARKUS_DATASOURCE_PASSWORD=th_betterme;QUARKUS_DATASOURCE_REACTIVE_URL=vertx-reactive:postgresql://localhost/tealhelix;QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost/tealhelix`

You need to make sure the IDE runner resolves workspace artifacts.

### From Docker

**TODO**


## Releases

```bash
git checkout master
# merge appropriately
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=x.y.z
git commit -am "Version x.y.z"
git tag -am "Version x.y.z" vx.y.z
git checkout develop
git merge master
mvn versions:set -DnewVersion=1.0.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Continue development with 1.0.0-SNAPSHOT"
git push --tags origin master:master develop:develop
```
