
MATCH (child:ContentElement) where child.parentId IS NOT NULL
WITH child MATCH (parent:ContentElement {id: child.parentId})
MERGE (child)-[:HAS_PARENT]->(parent);