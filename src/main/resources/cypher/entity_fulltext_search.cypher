CALL db.index.fulltext.queryNodes($fulltextIndex, $searchText)
YIELD node AS m, score
WHERE score IS NOT NULL AND any(label IN labels(m) WHERE label IN $labels)
WITH collect({node: m, score: score}) AS results, max(score) AS maxScore
  WHERE maxScore IS NOT NULL AND maxScore > 0
UNWIND results AS result
WITH result.node AS match,
     COALESCE(result.score / maxScore, 0.0) AS score,
     result.node.name AS name,
     result.node.description AS description,
     result.node.id AS id,
     labels(result.node) AS labels
  WHERE score >= $similarityThreshold
RETURN {
         name:        COALESCE(name, ''),
         description: COALESCE(description, ''),
         id:          COALESCE(id, ''),
         properties:  properties(match),
         labels:      labels,
         score:       score
       } AS result
  ORDER BY result.score DESC
