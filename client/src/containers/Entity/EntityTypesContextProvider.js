import React, { createContext, useCallback, useMemo } from 'react';
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
];

const processEntityTypeConfig = ({ color, ...entityTypeConfig }) => {
  const entityType = entityTypeConfig['entity-type'] || '';
  return {
    ...entityTypeConfig,
    entityType: entityType,
    displayName: capitalize(entityType.replace(/-/g, ' ')),
    path: `/${entityType}`,
    theme: color
      ? createMuiTheme({
          palette: {
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
  value: PropTypes.objectOf({
    'entity-type': PropTypes.string.isRequired,
  }),
};

export default function EntityTypesContextProvider(props) {
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
            ],
          }),
        () => authorizedFetch('/api/entity'),
        true
      ),
    []
  );
  const { data } = useDataFetch(memoizedFetchFunc, []);
  const entityTypesAll = useMemo(
    () => {
      const entityTypesMapLocal = new Map(
        ENTITY_TYPES_CONFIG_LOCAL.map((entityTypeConfig) => [
          entityTypeConfig['entity-type'],
          entityTypeConfig,
        ])
      );
      const entityTypesMapRemote = new Map(
        (data['entity-types'] || []).map((entityTypeConfig) => [
          entityTypeConfig['entity-type'],
          entityTypeConfig,
        ])
      );
      const entityTypesMapCombined = [
        ...entityTypesMapLocal,
        ...entityTypesMapRemote,
      ].reduce((result, [entityType, entityTypeConfig]) => {
        // merge the local and remote configs, where remote config overrides the local one
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
              (entityTypeConfig['generic?'] ||
                entityTypesMapLocal.has(entityType))
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
