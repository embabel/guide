CALL db.index.vector.queryNodes($vectorIndex, $topK, $queryVector)
YIELD node AS chunk, score
  WHERE score >= $similarityThreshold
RETURN {
         text:  chunk.text,
         id:    chunk.id,
         score: score
       } AS result
  ORDER BY result.score DESC
