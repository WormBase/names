import React, { createContext, useContext, useCallback } from 'react';
import { AuthorizationContext, useDataFetch } from '../Authenticate';

export const EntityTypesContext = createContext([]);

export default function EntityTypesContextProvider(props) {
  const { authorizedFetch } = useContext(AuthorizationContext);
  const memoizedFetchFunc = useCallback(
    () => () => authorizedFetch('/api/entity'),
    [authorizedFetch]
  );
  const { data } = useDataFetch(memoizedFetchFunc, []);
  return <EntityTypesContext.Provider value={data} {...props} />;
}
