import React, { createContext, useCallback } from 'react';
import { useDataFetch } from '../Authenticate';

export const EntityTypesContext = createContext([]);

export default function EntityTypesContextProvider(props) {
  const memoizedFetchFunc = useCallback(
    () => (authorizedFetch) => authorizedFetch('/api/entity'),
    []
  );
  const { data } = useDataFetch(memoizedFetchFunc, []);
  return <EntityTypesContext.Provider value={data} {...props} />;
}
