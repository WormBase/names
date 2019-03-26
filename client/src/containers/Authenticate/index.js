import React, { Component } from 'react';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';

const DEFAULT_AUTHENTICATION_STATE = {
  isAuthenticated: undefined,
  user: {
    name: null,
    email: null,
    id_token: null,
  },
  errorMessage: null, //JSON.stringify({a: 100}, undefined, 2),
};

export const AuthorizationContext = React.createContext({
  ...DEFAULT_AUTHENTICATION_STATE,
  handleLogin: () => {},
  handleLogout: () => {},
  authorizedFetch: () => {},
});

class Authenticate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      ...DEFAULT_AUTHENTICATION_STATE,
    };
  }

  handleLogin = () => {
    this.auth2.signIn().catch((error) => {
      this.setState({
        ...DEFAULT_AUTHENTICATION_STATE,
        isAuthenticated: false,
        errorMessage: JSON.stringify(error, undefined, 2),
      });
    });
  };

  handleLogout = () => {
    this.auth2.disconnect(); // revoke scopes
    this.auth2.signOut();
  };

  componentDidMount() {
    this.initializeSignIn();
  }

  // adapted from https://developers.google.com/identity/sign-in/web/build-button
  initializeSignIn = () => {
    const gapi = window.gapi;
    gapi.load('auth2', () => {
      // Retrieve the singleton for the GoogleAuth library and set up the client.
      this.auth2 = gapi.auth2.init({
        client_id:
          '514830196757-8464k0qoaqlb4i238t8o6pc6t9hnevv0.apps.googleusercontent.com',
        cookiepolicy: 'single_host_origin',
        // Request scopes in addition to 'profile' and 'email'
        //scope: 'additional_scope'
      });
      this.auth2.isSignedIn.listen((isSignedIn) => {
        if (isSignedIn) {
          const googleUser = this.auth2.currentUser.get();
          const googleUserProfile = googleUser.getBasicProfile();
          const name = googleUserProfile.getName();
          const email = googleUserProfile.getEmail();
          const id_token = googleUser.getAuthResponse().id_token;
          this.setState({
            ...DEFAULT_AUTHENTICATION_STATE,
            user: {
              name,
              email,
              id_token,
            },
            isAuthenticated: true,
          });
        } else {
          this.setState({
            ...DEFAULT_AUTHENTICATION_STATE,
            isAuthenticated: false,
          });
        }
      });
      this.auth2.then(() => {
        if (!this.auth2.isSignedIn.get()) {
          // now we know for certain the user isn't signed in, instead of auth initialization taking a while
          this.setState({
            isAuthenticated: false,
          });
        }
      });
    });
  };

  authorizedFetch = (url, options = {}) => {
    const { headers, ...otherOptions } = options;
    const newHeaders = new Headers(headers);
    const token = this.state.user.id_token;
    newHeaders.append('Authorization', `Token ${token}`);
    newHeaders.append('Content-Type', 'application/json');
    newHeaders.append('Accept', 'application/json');
    return fetch(url, {
      ...otherOptions,
      headers: newHeaders,
    }).then((response) => {
      if (response.status === 401) {
        this.setState({
          ...DEFAULT_AUTHENTICATION_STATE,
        });
      }
      return response;
    });
  };

  render() {
    return (
      <AuthorizationContext.Provider
        value={{
          ...this.state,
          handleLogin: this.handleLogin,
          handleLogout: this.handleLogout,
          authorizedFetch: this.authorizedFetch,
        }}
      >
        {this.props.children}
      </AuthorizationContext.Provider>
    );
  }
}

Authenticate.propTypes = {};

export default Authenticate;

export { ProfileButton, Login, Logout, Profile };
