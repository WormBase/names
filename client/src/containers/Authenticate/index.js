import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
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

function authorizedFetch(url, options) {
  const {headers, ...otherOptions} = options;
  const newHeaders = new Headers(headers);
  console.log(getStoredState());
  const userState = getStoredState();
  const token = userState ? userState.user.id_token : '';
  newHeaders.append('Authorization', `Token ${token}`);
  newHeaders.append('Content-Type', 'application/json');
  return fetch(url, {
    ...otherOptions,
    headers: newHeaders,
  });
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
    this.props.history.push('/');
  }

  componentDidMount() {
    const saveState = getStoredState();
    if (saveState) {
      this.setState({
        ...saveState
      });
    }
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
        profile: <Profile {...user}>
          {logout}
        </Profile>,
      }
    );
  }
}

Authenticate.propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }),
}

export default withRouter(Authenticate);

export {
  ProfileButton,
  authorizedFetch,
}
