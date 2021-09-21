# `DRUGCENTRAL-TOOLS`

Client tools, including original prototype web app, example SQL, example R, 
and workflow for Docker container of PostgreSql db.

* See also [BioClients](https://github.com/jeremyjyang/BioClients) for DrugCentral Python API (for Pg db).

## Dependencies

* Java 1.8
* Maven 3.6+
* Docker (Ubuntu 20.04, PostgreSql 10)
* [CDK \(Chemistry Development Kit\)](https://cdk.github.io/)
* [JSME \(JavaScript Molecular Editor\)](https://peter-ertl.com/jsme/)
* [RDKit](https://rdkit.org/)

## Compilation

```
mvn clean install
```

## Execution

Command-line app:

```
mvn --projects unm_biocomp_drugcentral exec:java -Dexec.mainClass="edu.unm.health.biocomp.drugcentral.drugcentral_app" -Dexec.args="-dbhost localhost -dbport 5432 -dbname drugcentral -dbusr drugman -dbpw dosage"
```

## Docker and DockerHub

* [DockerHub:drugcentral\_db](https://hub.docker.com/repository/docker/unmtransinfo/drugcentral_db)
* See: `Go_DockerBuild.sh`, `Go_DockerPush.sh`, `Go_DockerRun.sh`

## Deploying to public AWS instance

* See: `Go_Ubuntu_DockerInstall.sh`, `Go_DockerHubPull.sh`, `Go_DockerRun.sh`

## Deploying `DRUGCENTRAL_WAR`

Ok for Tomcat v8/v9 also, apparently.

```
mvn --projects drugcentral_war tomcat7:deploy
```

or

```
mvn --projects drugcentral_war tomcat7:redeploy
```

## Testing with Jetty

<http://localhost:8080/convert>, etc.

```
mvn --projects drugcentral_war jetty:run
```

