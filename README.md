# `DRUGCENTRAL-TOOLS`

Client tools, including original prototype web app, example SQL, example R, 
and workflow for Docker container of PostgreSql db.

* See also [BioClients](https://github.com/jeremyjyang/BioClients) for DrugCentral Python API (for Pg db).

## Dependencies

* Java 1.8
* Maven 3.5+
* Docker (Ubuntu 18.04, PostgreSql 10)
* [CDK \(Chemistry Development Kit\)](https://cdk.github.io/)
* [JSME \(JavaScript Molecular Editor\)](http://peter-ertl.com/jsme/)
* [RDKit](http://rdkit.org/)

## Compilation

```
mvn clean install
```

## Docker

* [DockerHub:drugcentral\_db](https://hub.docker.com/repository/docker/unmtransinfo/drugcentral_db)
* See: `Go_DockerBuild_Db.sh`, `Go_DockerPush_Db.sh`, `Go_DockerRun.sh`

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

