# `DRUGCENTRAL-TOOLS`

Client tools, including original prototype web app,
example SQL, example R, 
and workflow for Docker container of PostgreSql db.

* See also [BioClients](https://github.com/jeremyjyang/BioClients) for DrugCentral Python API (for Pg db).

___NOT___ required for DrugCentral build.

## Dependencies

* Java 1.8
* Maven 3.5+
* Docker (Ubuntu 18.04, PostgreSql 10)

## Compilation

```
mvn clean install
```

## Docker

* [DockerHub:drugcentral\_db](https://hub.docker.com/repository/docker/unmtransinfo/drugcentral_db)
* See: `Go_DockerBuild_Db.sh`, `Go_DockerPush_Db.sh`, `Go_DockerRun.sh`
