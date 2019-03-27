import React from 'react';

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

export default AuthorizationContext;
