CALL db.index.vector.queryNodes($index, $topK, $queryVector)
YIELD node AS m, score
  WHERE score >= $similarityThreshold
  AND any(label IN labels(m) WHERE label IN $labels)
RETURN {
         properties:  properties(m),
         name:        COALESCE(m.name, ''),
         description: COALESCE(m.description, ''),
         id:          COALESCE(m.id, ''),
         labels:      labels(m),
         score:       score
       } AS result
  ORDER BY result.score DESC
