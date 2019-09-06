export function getEntityPath(entityType) {
  return `/${entityType}`;
}

export const ENTITY_TYPES = ['gene', 'variation'].map((entityType) => ({
  entityType: entityType,
  path: getEntityPath(entityType),
}));

const ENTITY_TYPE_SET = new Set(
  ENTITY_TYPES.map(({ entityType }) => entityType)
);

export function existsEntitiyType(entityType) {
  return ENTITY_TYPE_SET.has(entityType);
}
