import React, { useEffect, useReducer, useContext, useCallback } from 'react';

export const DEFAULT_AUTHENTICATION_STATE = {
  isAuthenticated: undefined,
  user: {
    name: null,
    email: null,
    id_token: null,
  },
  errorMessage: null, //JSON.stringify({a: 100}, undefined, 2),
};

const AuthorizationContext = React.createContext({
  ...DEFAULT_AUTHENTICATION_STATE,
  handleLogin: () => {},
  handleLogout: () => {},
  authorizedFetch: () => {},
});

// adapted from Hooks examples in:
// https://overreacted.io/a-complete-guide-to-useeffect/
// https://www.robinwieruch.de/react-hooks-fetch-data/

export function useDataFetch(initialFetchFuncMemoized, initialData) {
  const { authorizedFetch } = useContext(AuthorizationContext);
  const [state, dispatch] = useReducer(dataFetchReducer, {
    fetchFunc: initialFetchFuncMemoized,
    data: initialData,
    dataTimestamp: 0,
    isLoading: false,
    isError: null,
    isNew: true,
    retryCounter: 0,
  });
  const { fetchFunc, retryCounter } = state;
  const setFetchFunc = useCallback(
    (newFetchFuncMemoized) => {
      dispatch({
        type: 'SET_FETCH_FUNCTION',
        payload: newFetchFuncMemoized,
      });
    },
    [dispatch]
  );
  const refetch = useCallback(
    () =>
      dispatch({
        type: 'REFETCH',
      }),
    [dispatch]
  );

  function dataFetchReducer(state, action) {
    console.log(action);
    switch (action.type) {
      case 'FETCH_INIT':
        return {
          ...state,
          isNew: false,
          isLoading: true,
          isError: null,
        };
      case 'FETCH_SUCCESS':
        return {
          ...state,
          isLoading: false,
          isError: null,
          data: action.payload,
          dataTimestamp: Date.now(),
        };
      case 'FETCH_FAILURE':
        return {
          ...state,
          isLoading: false,
          isError: action.payload,
        };
      case 'SET_FETCH_FUNCTION':
        return {
          ...state,
          fetchFunc: action.payload,
        };
      case 'REFETCH':
        return {
          ...state,
          retryCounter: state.retryCounter + 1,
        };
      default:
        throw new Error();
    }
  }

  useEffect(
    () => {
      let didCancel = false;

      function fetchData() {
        if (!fetchFunc || !authorizedFetch) {
          return;
        }
        dispatch({ type: 'FETCH_INIT' });

        return fetchFunc(authorizedFetch)
          .then((response) => {
            return Promise.all([response, response.json()]);
          })
          .then(([response, data]) => {
            if (!didCancel) {
              if (response.ok) {
                dispatch({ type: 'FETCH_SUCCESS', payload: data });
              } else {
                dispatch({
                  type: 'FETCH_FAILURE',
                  payload: data,
                });
              }
            }
          })
          .catch(() => {
            // error handling
            if (!didCancel) {
              dispatch({ type: 'FETCH_FAILURE' });
            }
          });
      }

      fetchData();

      return () => {
        // cleanup before effect is applied to the next render
        // avoids race condition when response came back out of order
        didCancel = true;
      };
    },
    [dispatch, fetchFunc, retryCounter, authorizedFetch]
  );

  return {
    ...state,
    isSuccess: !state.isNew && !state.isLoading && !state.isError,
    setFetchFunc,
    refetch,
  };
}

export default AuthorizationContext;
