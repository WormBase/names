import React, { useContext, useState, useEffect, useReducer } from 'react';

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

export function useDataFetch(initialUrl, initialData) {
  const { authorizedFetch } = useContext(AuthorizationContext);
  const [url, setUrl] = useState(initialUrl);
  const [state, dispatch] = useReducer(dataFetchReducer, {
    data: initialData,
    isLoading: false,
    isError: false,
  });

  function dataFetchReducer(state, action) {
    console.log(action);
    switch (action.type) {
      case 'FETCH_INIT':
        return {
          ...state,
          isLoading: true,
          isError: false,
        };
      case 'FETCH_SUCCESS':
        return {
          ...state,
          isLoading: false,
          isError: false,
          data: action.payload,
        };
      case 'FETCH_FAILURE':
        return {
          ...state,
          isLoading: false,
          isError: true,
        };
      default:
        throw new Error();
    }
  }

  useEffect(
    () => {
      let didCancel = false;

      function fetchData() {
        if (!url) {
          return;
        }
        dispatch({ type: 'FETCH_INIT' });
        return authorizedFetch(url, {
          method: 'GET',
        })
          .then((response) => {
            return Promise.all([response, response.json()]);
          })
          .then(([response, data]) => {
            if (!didCancel) {
              if (response.ok) {
                dispatch({ type: 'FETCH_SUCCESS', payload: data });
              } else {
                dispatch({ type: 'FETCH_FAILURE' });
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
    [authorizedFetch, dispatch, url]
  );

  return { ...state, url, setUrl };
}

export default AuthorizationContext;
