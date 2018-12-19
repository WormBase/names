import React, { Component } from 'react';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';

const DEFAULT_AUTHENTICATION_STATE = {
  isAuthenticated: false,
  user: {
    name: null,
    email: null,
    id_token: null,
  },
  errorMessage: null, //JSON.stringify({a: 100}, undefined, 2),
};

const sessionStorageKey = 'AUTHENTICATION_STATE';

function getStoredState() {
  const saveStateJSON = window.sessionStorage.getItem(sessionStorageKey);
  return saveStateJSON && JSON.parse(saveStateJSON);
}

class Authenticate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      ...DEFAULT_AUTHENTICATION_STATE,
    };
  }

  handleLogin = () => {
    this.auth2
      .signIn()
      .then((googleUser) => {
        const googleUserProfile = googleUser.getBasicProfile();
        const name = googleUserProfile.getName();
        const email = googleUserProfile.getEmail();
        const id_token = googleUser.getAuthResponse().id_token;
        this.setState(
          {
            ...DEFAULT_AUTHENTICATION_STATE,
            user: {
              name,
              email,
              id_token,
            },
            isAuthenticated: true,
          },
          () => {
            window.sessionStorage.setItem(
              sessionStorageKey,
              JSON.stringify(this.state)
            );
          }
        );
      })
      .catch((error) => {
        this.setState(
          {
            ...DEFAULT_AUTHENTICATION_STATE,
            isAuthenticated: false,
            errorMessage: JSON.stringify(error, undefined, 2),
          },
          () => {
            window.sessionStorage.setItem(
              sessionStorageKey,
              JSON.stringify(this.state)
            );
          }
        );
      });
  };

  handleLogout = () => {
    this.auth2.disconnect(); // revoke scopes
    this.auth2.signOut().then(() => {
      window.sessionStorage.removeItem(sessionStorageKey);
      this.setState({
        ...DEFAULT_AUTHENTICATION_STATE,
      });
    });
  };

  componentDidMount() {
    const saveState = getStoredState();
    if (saveState) {
      this.setState({
        ...saveState,
      });
    }
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
    console.log(this.state);
    const { user } = this.state;
    const logout = <Logout onLogout={this.handleLogout} />;
    return this.props.children({
      isAuthenticated: this.state.isAuthenticated,
      user: { ...user },
      login: (
        <Login
          onSignIn={this.handleLogin}
          errorMessage={this.state.errorMessage}
        />
      ),
      logout: logout,
      authorizedFetch: this.authorizedFetch,
      profile: <Profile {...user}>{logout}</Profile>,
    });
  }
}

Authenticate.propTypes = {};

export default Authenticate;

export { ProfileButton };
