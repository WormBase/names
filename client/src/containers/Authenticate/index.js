import React, { Component } from 'react';
import PropTypes from 'prop-types';
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
    }
  }

  handleLoginSuccess = (user, logout) => {
    const {name, email, id_token} = user;
    this.logout = logout;
    this.setState({
      ...DEFAULT_AUTHENTICATION_STATE,
      user: {
        name,
        email,
        id_token,
      },
      isAuthenticated: true,
    }, () => {
      window.sessionStorage.setItem(sessionStorageKey, JSON.stringify(this.state));
    });
  }

  handleLoginError = (error) => {
    this.setState({
      ...DEFAULT_AUTHENTICATION_STATE,
      isAuthenticated: false,
      errorMessage: JSON.stringify(error, undefined, 2),
    }, () => {
      window.sessionStorage.setItem(sessionStorageKey, JSON.stringify(this.state));
    });
  }

  handleLogout = () => {
    this.setState({
      ...DEFAULT_AUTHENTICATION_STATE,
    });
    window.sessionStorage.removeItem(sessionStorageKey);
    this.logout && this.logout();
  }

  componentDidMount() {
    const saveState = getStoredState();
    if (saveState) {
      this.setState({
        ...saveState
      });
    }
  }

  authorizedFetch = (url, options = {}) => {
    const {headers, ...otherOptions} = options;
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
  }


  render() {
    console.log(this.state);
    const {user} = this.state;
    const logout = <Logout onLogout={this.handleLogout} />;
    return this.props.children(
      {
        isAuthenticated: this.state.isAuthenticated,
        user: {...user},
        login: <Login
          onSuccess={this.handleLoginSuccess}
          onError={this.handleLoginError}
          errorMessage={this.state.errorMessage}
        />,
        logout: logout,
        authorizedFetch: this.authorizedFetch,
        profile: <Profile {...user}>
          {logout}
        </Profile>,
      }
    );
  }
}

Authenticate.propTypes = {
};

export default Authenticate;

export {
  ProfileButton,
}
