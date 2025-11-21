MATCH (chunk:$($chunkNodeName) {id: $basisId})
CREATE (chunk)-[:HAS_ENTITY]->(e:$($entityLabels) {
id: $id,
name: $name,
description: $description,
createdDate: timestamp()
})
SET e += $properties,
e.lastModifiedDate = timestamp()
RETURN {
id: e.id,
nodesCreated: COUNT(e)
} AS result
