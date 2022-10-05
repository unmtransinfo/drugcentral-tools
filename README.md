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
java -classpath unm_biocomp_drugcentral-0.0.1-SNAPSHOT-jar-with-dependencies.jar edu.unm.health.biocomp.drugcentral.drugcentral_app -dbhost localhost -dbport 5432 -dbname drugcentral -dbusr drugman -dbpw dosage
```

## Docker and DockerHub

* [DockerHub:drugcentral\_db](https://hub.docker.com/repository/docker/unmtransinfo/drugcentral_db)
* See: `Go_dc_DockerBuild.sh`, `Go_dc_DockerPush.sh`, `Go_dc_DockerRun.sh`

## Deploying to public AWS instance

* See: `Go_Ubuntu_DockerInstall.sh`, `Go_dc_DockerHubPull.sh`, `Go_dc_DockerRun.sh`

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

## Configuring RDKit Cartridge

Configuring RDKit Cartridge provides for chemical structure searching and other
cheminformatics functionality. The database as available via PostgreSql dump is
not configured with the RDKit cartridge and molecule object column, for greater
compatibility across operating systems and PostgreSql versions.
For Ubuntu 20.04LTS or 22.04LTS, and PostgreSql 14, the PostgreSql Cartridge can
be installed with this command:

```
apt install postgresql-14-rdkit
```

To configure these features, connect to the database,as database owner, and 
use the following SQL:

```
CREATE EXTENSION rdkit;
ALTER TABLE structures ADD m MOL;
UPDATE structures SET m = mol_from_smiles(smiles::cstring) WHERE smiles IS NOT NULL;
CREATE INDEX molidx ON structures USING gist(m);
```
