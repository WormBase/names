import React, { createContext, useCallback, useMemo, useContext } from 'react';
import PropTypes from 'prop-types';
import {
  createMuiTheme,
  theme as defaultTheme,
} from '../../components/elements';
import {
  green as geneColor,
  purple as variationColor,
} from '@material-ui/core/colors';
import { useDataFetch } from '../Authenticate';
import { mockFetchOrNot } from '../../mock';
import { capitalize } from '../../utils/format';

const ENTITY_TYPES_CONFIG_LOCAL = [
  {
    'entity-type': 'gene',
    color: geneColor['A400'],
  },
  { 'entity-type': 'variation', color: variationColor['A700'] },
].map((entityTypeConfig) => ({
  ...entityTypeConfig,
  'local?': true,
}));

const processEntityTypeConfig = ({ color, ...entityTypeConfig }) => {
  const entityType = entityTypeConfig['entity-type'] || '';
  return {
    ...entityTypeConfig,
    entityType: entityType,
    displayName: capitalize(entityType.replace(/-/g, ' ')),
    path: `/${entityType}`,
    apiPrefix: entityTypeConfig['generic?']
      ? `/api/entity/${entityType}`
      : `/api/${entityType}`,
    theme: color
      ? createMuiTheme({
          palette: {
            ...defaultTheme.palette,
            secondary: {
              main: color,
            },
          },
        })
      : defaultTheme,
  };
};

export const EntityTypesContext = createContext([]);
EntityTypesContext.Provider.propTypes = {
  value: PropTypes.objectOf(
    PropTypes.shape({
      'entity-type': PropTypes.string.isRequired,
    })
  ),
};

export function EntityTypesContextProvider(props) {
  const memoizedFetchFunc = useCallback(
    (authorizedFetch) =>
      mockFetchOrNot(
        (mockFetch) =>
          mockFetch.get('*', {
            'entity-types': [
              {
                'entity-type': 'gene',
                'generic?': false,
                'enabled?': true,
              },
              {
                'entity-type': 'sequence-feature',
                'generic?': true,
                'enabled?': true,
              },
              {
                'entity-type': 'variation',
                'generic?': true,
                'enabled?': true,
              },
              {
                'entity-type': 'strain',
                'generic?': true,
                'enabled?': true,
              },
            ],
          }),
        () => authorizedFetch('/api/entity'),
        false
      ),
    []
  );
  const { data } = useDataFetch(memoizedFetchFunc, []);
  const entityTypesAll = useMemo(
    () => {
      const entityTypesMapCombined = [
        ...ENTITY_TYPES_CONFIG_LOCAL,
        ...(data['entity-types'] || []),
      ].reduce((result, entityTypeConfig) => {
        // merge the local and remote configs, where remote config overrides the local one
        const entityType = entityTypeConfig['entity-type'];
        const entityTypeConfigPrev = result.get(entityType);
        result.set(
          entityType,
          entityTypeConfigPrev
            ? { ...entityTypeConfigPrev, ...entityTypeConfig }
            : entityTypeConfig
        );
        return result;
      }, new Map([]));
      const entityTypesMap = new Map(
        [...entityTypesMapCombined]
          .filter(([entityType, entityTypeConfig]) => {
            return (
              entityTypeConfig['enabled?'] &&
              (entityTypeConfig['generic?'] || entityTypeConfig['local?'])
            );
          })
          .map(([entityType, entityTypeConfig]) => [
            entityType,
            processEntityTypeConfig(entityTypeConfig),
          ])
      );
      return entityTypesMap;
    },
    [data]
  );
  return <EntityTypesContext.Provider value={entityTypesAll} {...props} />;
}

export default function useEntityTypes() {
  const entityTypesMap = useContext(EntityTypesContext);
  const entityTypesAll = useMemo(
    () => [...entityTypesMap].map(([, entityTypeConfig]) => entityTypeConfig),
    [entityTypesMap]
  );
  const getEntityType = useCallback(
    (entityType) => entityTypesMap.get(entityType),
    [entityTypesMap]
  );
  return {
    entityTypesAll: entityTypesAll,
    getEntityType: getEntityType,
  };
}
