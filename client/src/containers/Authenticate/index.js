import React, { useMemo, useCallback } from 'react';
import { useSessionStorageReducer } from 'react-storage-hooks';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';
import TokenMgmt from './TokenMgmt';
import AuthorizationContext, {
  DEFAULT_AUTHENTICATION_STATE,
} from './AuthorizationContext';
import useDataFetch from './useDataFetch';
import { googleLogout, useGoogleLogin } from '@react-oauth/google';

export default function Authenticate({ children }) {
  const [state, dispatch] = useSessionStorageReducer(
    'authentication',
    reducer,
    {
      ...DEFAULT_AUTHENTICATION_STATE,
    }
  );

  function reducer(state, { type, payload }) {
    switch (type) {
      case 'LOGIN_BEGIN':
        return {
          ...state,
          isAuthenticated: undefined,
        };
      case 'LOGIN_FAILURE':
        return {
          ...state,
          isAuthenticated: false,
          errorMessage: JSON.stringify(payload.error, undefined, 2),
        };
      case 'LOGIN_SUCCESS':
        return {
          ...state,
          isAuthenticated: true,
          user: payload.user,
        };
      case 'ACCESS_REVOKED':
        return {
          ...DEFAULT_AUTHENTICATION_STATE,
          isAuthenticated: false,
        };
      default:
        throw new Error();
    }
  }

  const locationHref = window.location.href;

  const handleLogin = useGoogleLogin({
    flow: 'auth-code',
    onSuccess: (codeResponse) => onLoginSuccess(codeResponse),
    onError: (error) => dispatch({ type: 'LOGIN_FAILURE', payload: { error } }),
    redirect_uri: locationHref,
  });

  function onLoginSuccess(codeResponse) {
    console.log('Successful login received. Getting user info.');

    getUserInfo(codeResponse);
  }

  function getUserInfo(gooleLoginResponse) {
    console.log('Retrieving user info using Google Login response.');

    const identityHeaders = new Headers();
    identityHeaders.append('Authorization', `Token ${gooleLoginResponse.code}`);
    identityHeaders.append('Content-Type', 'application/json');
    identityHeaders.append('Accept', 'application/json');

    dispatch({ type: 'LOGIN_BEGIN' });

    var return_val;

    try {
      return_val = fetch(`/api/auth/identity`, {
        headers: identityHeaders,
      }).then((response) => {
        if (response.ok) {
          response.json().then((identity) => {
            console.log(
              'Successfull identity response received. User authorized.'
            );
            const name = identity.person.name;
            const email = identity.person.email;
            const wb_person_id = identity.person.id;
            const id_token = identity['id-token'];
            dispatch({
              type: 'LOGIN_SUCCESS',
              payload: {
                user: {
                  name: name,
                  email: email,
                  id_token: id_token,
                  id: wb_person_id,
                },
              },
            });
          });
        } else {
          response.text().then((error) => {
            dispatch({ type: 'LOGIN_FAILURE', payload: { error } });
          });
        }
      });

      return return_val;
    } catch (err) {
      console.log(err);
      dispatch({ type: 'ACCESS_REVOKED' });
    }
  }

  const handleLogout = useCallback(
    () => {
      googleLogout();
      dispatch({ type: 'ACCESS_REVOKED' });
    },
    [dispatch]
  );

  const authorizedFetch = useCallback(
    (url, options = {}) => {
      const { headers, ...otherOptions } = options;
      const newHeaders = new Headers(headers);
      const token = state.user.id_token;
      newHeaders.append('Authorization', `Token ${token}`);
      newHeaders.append('Content-Type', 'application/json');
      newHeaders.append('Accept', 'application/json');
      return fetch(url, {
        ...otherOptions,
        headers: newHeaders,
      }).then((response) => {
        if (response.status === 401) {
          dispatch({ type: 'ACCESS_REVOKED' });
        }
        return response;
      });
    },
    [state.user, dispatch]
  );

  const authorizationContextValue = useMemo(
    () => {
      return {
        ...state,
        handleLogin: handleLogin,
        handleLogout: handleLogout,
        authorizedFetch: authorizedFetch,
      };
    },
    [state, handleLogin, handleLogout, authorizedFetch]
  );
  return (
    <AuthorizationContext.Provider value={authorizationContextValue}>
      {children}
    </AuthorizationContext.Provider>
  );
}

Authenticate.propTypes = {};

export {
  ProfileButton,
  Login,
  Logout,
  Profile,
  TokenMgmt,
  AuthorizationContext,
  useDataFetch,
};
