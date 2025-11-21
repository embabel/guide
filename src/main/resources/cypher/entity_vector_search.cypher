CALL db.index.vector.queryNodes($index, $topK, $queryVector)
YIELD node AS m, score
WHERE score >= $similarityThreshold AND
any(label IN labels(m) WHERE label IN $labels)
RETURN properties(m) AS properties, m.name as name, m.description as description, m.id AS id, labels(m) AS labels,
       score
ORDER BY score DESC

