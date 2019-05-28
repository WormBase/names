import React, { useMemo, useReducer, useCallback, useEffect } from 'react';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';
import AuthorizationContext, {
  DEFAULT_AUTHENTICATION_STATE,
  useDataFetch,
} from './AuthorizationContext';

export default function Authenticate({ children }) {
  const [state, dispatch] = useReducer(reducer, {
    ...DEFAULT_AUTHENTICATION_STATE,
  });

  function reducer(state, { type, payload }) {
    switch (type) {
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

  let auth2;

  useEffect(() => {
    // adapted from https://developers.google.com/identity/sign-in/web/build-button
    const gapi = window.gapi;
    gapi.load('auth2', () => {
      // Retrieve the singleton for the GoogleAuth library and set up the client.
      auth2 = gapi.auth2.init({
        client_id:
          '514830196757-8464k0qoaqlb4i238t8o6pc6t9hnevv0.apps.googleusercontent.com',
        cookiepolicy: 'single_host_origin',
        // Request scopes in addition to 'profile' and 'email'
        //scope: 'additional_scope'
      });
      auth2.isSignedIn.listen((isSignedIn) => {
        if (isSignedIn) {
          const googleUser = auth2.currentUser.get();
          const googleUserProfile = googleUser.getBasicProfile();
          const name = googleUserProfile.getName();
          const email = googleUserProfile.getEmail();
          const id_token = googleUser.getAuthResponse().id_token;
          dispatch({
            type: 'LOGIN_SUCCESS',
            payload: {
              user: {
                name,
                email,
                id_token,
              },
            },
          });
        } else {
          dispatch({ type: 'ACCESS_REVOKED' });
        }
      });
      auth2.then(() => {
        if (!auth2.isSignedIn.get()) {
          // now we know for certain the user isn't signed in, instead of auth initialization taking a while
          dispatch({ type: 'ACCESS_REVOKED' });
        }
      });
    });
  }, []);

  const handleLogin = useCallback(() => {
    auth2.signIn().catch((error) => {
      dispatch({ type: 'LOGIN_FAILURE', payload: { error } });
    });
  }, []);

  const handleLogout = useCallback(() => {
    auth2.disconnect(); // revoke scopes
    auth2.signOut();
  }, []);

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
    [state.user]
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
  AuthorizationContext,
  useDataFetch,
};
