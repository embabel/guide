// Useful admin queries

SHOW VECTOR INDEXES;


// Requires admin privileges
CALL apoc.schema.nodes() YIELD name, type
WHERE type = "VECTOR"
WITH collect(name) AS vectorIndexes
UNWIND vectorIndexes AS indexName
CALL apoc.cypher.doIt("DROP INDEX `" + indexName + "`", {}) YIELD value
RETURN count(*) AS droppedIndexes



CALL db.index.fulltext.queryNodes(`embabel-content-fulltext-index`, 'xy')
YIELD node AS chunk, score
  WHERE score >= $similarityThreshold
RETURN chunk.text AS text, chunk.id AS id,
       score
  ORDER BY score DESC