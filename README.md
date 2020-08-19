# Neo4j 4 Metabase driver

Wraps the Neo4j BI connector to allow Metabase use.

## Building the driver

### Prerequisites

#### Install Metabase as a local maven dependency, compiled for building drivers

Clone the [Metabase repo](https://github.com/metabase/metabase)

```bash
cd /path/to/metabase_source
lein install-for-building-drivers
```

#### Download and install the Neo4j BI connector

* Get the connector [here](https://neo4j.com/bi-connector/)

```bash
# cp the jar to the maven dir
mkdir -p .m2/repository/neo4j/neo4j-bi-jdbc/1.0.0 && cp JAR_PATH .m2/repository/neo4j/neo4j-bi-jdbc/1.0.0/
```

### Build the driver

```bash
# (In the directory where you cloned this repository)
lein clean
DEBUG=1 LEIN_SNAPSHOTS_IN_RELEASE=true lein uberjar
```

### Copy it to your plugins dir and restart Metabase

```bash
mkdir -p /path/to/metabase/plugins/
cp target/uberjar/neo4j.metabase-driver.jar /path/to/metabase/plugins/
jar -jar /path/to/metabase/metabase.jar
```

*or:*

```bash
mkdir -p /path/to/metabase_source/plugins
cp target/uberjar/neo4j.metabase-driver.jar /path/to/metabase_source/plugins/
cd /path/to/metabase_source
lein run
```

## What this looks like

![databases](screenshots/databases.png)
![tables](screenshots/tables.png)

## Caveats 

This is a WIP implementation.
It is not heavily tested and is not compatible with neo4j 3.5 ( even though the underlying driver is)

## TODO

* Support Cypher queries when BI connector matures
* Testing