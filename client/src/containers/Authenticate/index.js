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
  errorMessage: JSON.stringify({a: 100}, undefined, 2),
};

export default class Authenticate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      ...DEFAULT_AUTHENTICATION_STATE,
    }
  }

  handleLoginSuccess = (user) => {
    const {name, email, id_token} = user;
    this.setState({
      user: {
        name,
        email,
        id_token,
      },
      isAuthenticated: true,
    });
  }

  handleLoginError = (error) => {
    this.setState({
      errorMessage: JSON.stringify(error, undefined, 2),
    });
  }

  handleLogout = () => {
    this.setState({
      ...DEFAULT_AUTHENTICATION_STATE,
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
        profile: <Profile {...user}>
          {logout}
        </Profile>,
      }
    );
  }
}

export {
  ProfileButton,
}
