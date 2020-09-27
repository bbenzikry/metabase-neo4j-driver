FROM metabase/metabase:v0.36.2

ADD --chown=2000:2000 \
  https://github.com/bbenzikry/metabase-neo4j-driver/releases/download/v0.0.5/neo4j.metabase-driver.jar \
  /plugins/neo4j.neo4j-driver.jar