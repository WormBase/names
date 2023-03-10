import React, {
  useMemo,
  useState,
  useRef,
  //  useReducer,
  useCallback,
  useEffect,
} from 'react';
import { useSessionStorageReducer } from 'react-storage-hooks';
import axios from 'axios';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';
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

  const userRef = useRef([]);
  const profileRef = useRef([]);

  useEffect(() => {
    console.log('Change detected. Verifying if user is set.');
    const user = userRef.current;
    if (user && user.length) {
      console.log('User change detected. Retrieving profile for user', user);
      getUserInfo(user);
    } else {
      console.log('User not set, deleting profile.');
      profileRef.current = null;
    }
  }, []);

  const handleLogin = useGoogleLogin({
    flow: 'auth-code',
    onSuccess: (codeResponse) => onLoginSuccess(codeResponse),
    onError: (error) => dispatch({ type: 'LOGIN_FAILURE', payload: { error } }),
    // redirect_uri: 'urn:ietf:wg:oauth:2.0:oob'
    redirect_uri: 'http://lvh.me:3000',
    // redirect_uri: 'https://names.wormbase.org'
  });

  function onLoginSuccess(codeResponse) {
    console.log('Successful login received. Setting user.');
    console.log('codeResponse', codeResponse);
    userRef.current = codeResponse;
    console.log('New user:', userRef.current);

    getUserInfo(userRef.current);
  }

  function getUserInfo(user) {
    console.log('Retrieving user info for user', user);
    axios
      .get(
        `https://www.googleapis.com/oauth2/v3/userinfo?access_token=${
          user.access_token
        }`,
        {
          headers: {
            Authorization: `${user.token_type} ${user.access_token}`,
            Accept: 'application/json',
          },
        }
      )
      .then((res) => {
        const userInfo = res.data;
        console.log('Retrieved userInfo:', userInfo);

        // const id_token = user.getCredentialResponse().credential;
        const id_token = user.access_token;
        const user_code = user.code;
        const name = userInfo.name;
        const email = userInfo.email;

        const newHeaders = new Headers();
        newHeaders.append('Authorization', `Token ${user_code}`);
        newHeaders.append('Content-Type', 'application/json');
        newHeaders.append('Accept', 'application/json');

        dispatch({ type: 'LOGIN_BEGIN' });
        // Verify the backend API is working and
        // accepts the id_token
        return fetch(`/api/person/${email}`, {
          headers: newHeaders,
        }).then((response) => {
          if (response.ok) {
            response.json().then(({ id }) => {
              dispatch({
                type: 'LOGIN_SUCCESS',
                payload: {
                  user: {
                    name,
                    email,
                    user_code,
                    id,
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
      })
      .catch((err) => {
        console.log(err);

        dispatch({ type: 'ACCESS_REVOKED' });
      });
  }

  const handleLogout = useCallback(() => {
    googleLogout();
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
