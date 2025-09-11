// Useful admin queries

SHOW VECTOR INDEXES;


// Requires admin privileges
CALL apoc.schema.nodes() YIELD name, type
WHERE type = "VECTOR"
WITH collect(name) AS vectorIndexes
UNWIND vectorIndexes AS indexName
CALL apoc.cypher.doIt("DROP INDEX `" + indexName + "`", {}) YIELD value
RETURN count(*) AS droppedIndexes