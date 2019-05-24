import React, { useState, useEffect, useReducer } from 'react';

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

export function useDataFetch(initialFetchFunc, initialData) {
  const [state, dispatch] = useReducer(dataFetchReducer, {
    data: initialData,
    dataTimestamp: 0,
    isLoading: false,
    isError: null,
    isNew: true,
  });
  const [fetchFunc, setFetchFunc] = useState(initialFetchFunc);

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
      default:
        throw new Error();
    }
  }

  useEffect(
    () => {
      let didCancel = false;

      function fetchData() {
        if (!fetchFunc) {
          return;
        }
        dispatch({ type: 'FETCH_INIT' });

        return fetchFunc()
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
    [dispatch, fetchFunc]
  );

  return {
    ...state,
    isSuccess: !state.isNew && !state.isLoading && !state.isError,
    setFetchFunc,
  };
}

export default AuthorizationContext;
