import React, {
  useMemo,
  useRef,
  //  useReducer,
  useCallback,
  useEffect,
} from 'react';
import { useSessionStorageReducer } from 'react-storage-hooks';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';
import AuthorizationContext, {
  DEFAULT_AUTHENTICATION_STATE,
  useDataFetch,
} from './AuthorizationContext';

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

  const auth2Ref = useRef();

  useEffect(() => {
    // adapted from https://developers.google.com/identity/sign-in/web/build-button
    const gapi = window.gapi;
    gapi.load('auth2', () => {
      // Retrieve the singleton for the GoogleAuth library and set up the client.
      auth2Ref.current = gapi.auth2.init({
        client_id:
          '514830196757-8464k0qoaqlb4i238t8o6pc6t9hnevv0.apps.googleusercontent.com',
        cookiepolicy: 'single_host_origin',
        // Request scopes in addition to 'profile' and 'email'
        //scope: 'additional_scope'
      });
      auth2Ref.current.isSignedIn.listen((isSignedIn) => {
        if (isSignedIn) {
          const googleUser = auth2Ref.current.currentUser.get();
          const googleUserProfile = googleUser.getBasicProfile();
          const name = googleUserProfile.getName();
          const email = googleUserProfile.getEmail();
          const id_token = googleUser.getAuthResponse().id_token;

          const newHeaders = new Headers();
          newHeaders.append('Authorization', `Token ${id_token}`);
          newHeaders.append('Content-Type', 'application/json');
          newHeaders.append('Accept', 'application/json');
          // hack: initiate a request to verify the backend API is working and
          // accepts the id_token
          return fetch('/api/entity', {
            headers: newHeaders,
          }).then((response) => {
            if (response.ok) {
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
              response.json().then((content) => {
                dispatch({ type: 'LOGIN_FAILURE', payload: { content } });
              });
            }
          });
        } else {
          dispatch({ type: 'ACCESS_REVOKED' });
        }
      });
      auth2Ref.current.then(() => {
        if (!auth2Ref.current.isSignedIn.get()) {
          // now we know for certain the user isn't signed in, instead of auth initialization taking a while
          dispatch({ type: 'ACCESS_REVOKED' });
        }
      });
    });
  }, []);

  const handleLogin = useCallback(() => {
    auth2Ref.current.signIn().catch((error) => {
      dispatch({ type: 'LOGIN_FAILURE', payload: { error } });
    });
  }, []);

  const handleLogout = useCallback(() => {
    auth2Ref.current.disconnect(); // revoke scopes
    auth2Ref.current.signOut();
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
