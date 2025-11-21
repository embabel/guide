MERGE (e:ContentElement {id: $id})
SET e += $properties,
  e.lastModifiedDate = timestamp()
WITH e
CALL apoc.create.addLabels(e, $labels)
YIELD node
RETURN 1