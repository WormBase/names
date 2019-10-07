import { createMuiTheme } from '@material-ui/core';
import {
  green as geneColor,
  purple as variationColor,
} from '@material-ui/core/colors';

export function getEntityPath(entityType) {
  return `/${entityType}`;
}

export const ENTITY_TYPES = [
  {
    entityType: 'gene',
    color: geneColor['A400'],
  },
  { entityType: 'variation', color: variationColor['A700'] },
].map(({ entityType, color }) => ({
  entityType,
  path: getEntityPath(entityType),
  theme: createMuiTheme({
    palette: {
      secondary: {
        main: color,
      },
    },
  }),
}));
console.log(ENTITY_TYPES);

export function getEntityTypeTheme(entityType) {
  const { theme } =
    ENTITY_TYPES.filter(({ entityType: et }) => et === entityType)[0] || {};
  return theme;
}

const ENTITY_TYPE_SET = new Set(
  ENTITY_TYPES.map(({ entityType }) => entityType)
);

export function existsEntitiyType(entityType) {
  return ENTITY_TYPE_SET.has(entityType);
}

export function getAPIPrefix(entityType, wbId) {
  return `/api/entity/${entityType}/${wbId}`;
}
