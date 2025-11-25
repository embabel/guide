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

WITH value.anchorNode AS anchorNode, value.similar AS similarItems
RETURN {
         anchor: {
           id: anchorNode.id,
           uri: anchorNode.uri,
           text: anchorNode.text,
           parentId: anchorNode.parentId,
           ingestionDate: anchorNode.ingestionTimestamp,
           metadata_source: anchorNode.metadata.source,
           labels: labels(anchorNode)
         },
         similar: [item IN similarItems | {
           match: {
             id: item.match.id,
             uri: item.match.uri,
             text: item.match.text,
             parentId: item.match.parentId,
             ingestionDate: item.match.ingestionTimestamp,
             metadata_source: item.match.metadata.source,
             labels: labels(item.match)
           },
           score: item.score
         }],
         similarCount: size(similarItems)
       } as result
  ORDER BY result.similarCount DESC
