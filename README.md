# TealHelix

TealHelix aims to empower consumers with personalised and inclusive labelling solutions that promote sustainable
food choices for everyone.

This project is part of the ICT solutions of TealHelix.

## The build system

The build system is Maven and is configured by a set of properties and profiles, as follows:

### Build properties

The following properties are local to an environment; they can be specified as `-Dpropname=propvalue` command line arguments,
or placed in a local Maven profile in `~/.m2/settings.xml`.

### Build profiles

TODO

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

```shell
mvn package
# -OR-
mvn package -Pdocker # to build the docker images too
```

### Creating/updating the DB

Assuming that the properties are defined through a Maven profile, e.g. like the `tealhelix-local-postgres` in
`~/.m2/settings.xml` that was described above, run the following:

```shell
mvn process-resources -Pdbupdate-application,tealhelix-local-postgres
```

Otherwise, you have to specify the properties by command line—obviously much more cumbersome:

```shell
mvn process-resources -Pdbupdate-application -Ddatabase.application.jdbc.url=... -Ddatabase.application.username=... -Ddatabase.application.password=... -D...
```

#### Rolling back changes

Occasionally you may want to roll back some changes. Switch to the appropriate migration project and run
(example is for application migrations):

```shell
mvn org.liquibase:liquibase-maven-plugin:rollback \
	-Dliquibase.rollbackCount=... -Dliquibase.changeLogFile=src/main/resources/db.changelog.xml \
	-Dliquibase.promptOnNonLocalDatabase=false -Dliquibase.driver=org.postgresql.Driver \
	-Dliquibase.url=jdbc:postgresql://localhost/tealhelix -Dliquibase.username=tealhelix_application \
	-Dliquibase.password=tealhelix_application
```

Full info [here](https://docs.liquibase.com/tools-integrations/maven/commands/maven-rollback.html).

## Running

### Docker compose

This will be the first thing you need to run in a local development environment, as it launches all the
necessary peripheral services (e.g. the database).

```shell
cd tealhelix-docker/docker-compose/
docker-compose -f docker-compose-peripherals.yml -p tealhelix up -d    # the first time
docker-compose -f docker-compose-peripherals.yml -p tealhelix start    # to start
docker-compose -f docker-compose-peripherals.yml -p tealhelix stop     # to stop
docker-compose -f docker-compose-peripherals.yml -p tealhelix down     # to remove the containers, without removing the persistent volumes
docker-compose -f docker-compose-peripherals.yml -p tealhelix down -v  # to remove the containers, also removing the persistent volumes
```

### From command line

You need to `mvn install`, so that `quarkus:dev` will find the artifacts!

Cd to a Quarkus module and execute the `quarkus:dev` Maven goal. Don't forget to activate the profile that defines DB connection parameters. E.g.:

```shell
cd tealhelix-application-module/tealhelix-application
mvn -Ptealhelix-local-postgres quarkus:dev
```

### From command line (2)

From the project root, activate the profile of the microservice you want to run (don't forget the profile with the DB settings:

```shell
mvn package -Papplication-quarkus-dev,tealhelix-local-postgres
```

### From IDE

Run a Quarkus run configuration. You need to specify the DB connection parameters (and any other runtime parameters)
from the run configuration. For that, take a look at the `pom.xml` of the microservice you want to run. There is a
property `jvm.args`. You need to define those and give them the appropriate values (for IntelliJ, "Add VM options").

### From Docker

TODO
