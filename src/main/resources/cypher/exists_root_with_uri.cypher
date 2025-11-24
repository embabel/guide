MATCH (root:ContentElement {uri: $uri})
WHERE 'Document' IN labels(root) OR 'ContentRoot' IN labels(root)
RETURN count(root) > 0 AS exists
