MATCH (root:ContentElement {uri: $uri})
WHERE 'Document' IN labels(root) OR 'ContentRoot' IN labels(root)
OPTIONAL MATCH (root)<-[:HAS_PARENT*0..]-(descendant:ContentElement)
WITH collect(DISTINCT root) + collect(DISTINCT descendant) AS nodesToDelete
UNWIND nodesToDelete AS node
WITH DISTINCT node
DETACH DELETE node
RETURN count(*) AS deletedCount
