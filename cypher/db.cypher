CREATE VECTOR INDEX entity_embeddings IF NOT EXISTS
FOR (n:Entity)
ON (n.embedding)
OPTIONS {indexConfig: {
`vector.dimensions`: 1536,
`vector.similarity_function`: 'cosine'
}};

// Only create the index if it does not already exist
CREATE VECTOR INDEX `embabel-content-element-index` IF NOT EXISTS
FOR (n:ContentElement) ON (n.embedding)
OPTIONS {indexConfig: {
`vector.dimensions`: 1536,
`vector.similarity_function`: 'cosine'
}};



//CREATE FULLTEXT INDEX issue_fulltext FOR (n:Issue) ON EACH [n.title, n.body];
