export const ENTITY_TYPES = ['gene', 'variation'];

export const ENTITY_TYPE_PATHS = ENTITY_TYPES.map(
  (entityType) => `/${entityType}`
);

const ENTITY_TYPE_SET = new Set(ENTITY_TYPES);

export function existsEntitiyType(entityType) {
  return ENTITY_TYPE_SET.has(entityType);
}
