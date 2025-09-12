// Delete data
// You have been warned!

MATCH (n)
DETACH DELETE n;

DROP INDEX `embabel-content-fulltext-index`;

DROP INDEX `embabel-content-index`;

DROP INDEX `embabel-entity-fulltext-index`;

DROP INDEX `embabel-entity-index`;
