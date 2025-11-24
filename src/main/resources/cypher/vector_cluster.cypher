MATCH (n:$($labels))
WITH collect(n) as allNodes
CALL apoc.cypher.parallel(
"WITH $item as seedNode
 CALL db.index.vector.queryNodes($vectorIndex, $topK, seedNode.embedding)
 YIELD node AS m, score
 WHERE m <> seedNode AND score > $similarityThreshold
   AND id(seedNode) < id(m)
   AND any(label IN labels(m) WHERE label IN $labels)
 RETURN seedNode as anchorNode,
        collect({match: m, score: score}) as similar",
{
  item: allNodes,
  labels: $labels,
  topK: $topK,
  similarityThreshold: $similarityThreshold,
  vectorIndex: $vectorIndex
},
"item"
) YIELD value
  WHERE size(value.similar) > 0
RETURN value.anchorNode as anchor,
       value.similar as similar,
       size(value.similar) as similarCount
  ORDER BY similarCount DESC