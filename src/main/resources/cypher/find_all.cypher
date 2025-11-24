MATCH (existing:$($labels))
RETURN
  properties(existing) +
  { labels: labels(existing) } AS result
  LIMIT $limit
