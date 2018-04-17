import React, { Component } from 'react';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';

export default class Authenticate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      isAuthenticated: true,
      user: {
        name: null,
        email: null,
      },
      errorMessage: null,
    }
  }

  handleLoginSuccess = () => {

  }

  handleLoginError = () => {

  }

  render() {
    return this.props.children(
      {
        isAuthenticated: this.state.isAuthenticated,
        user: {...this.state.user},
        login: <Login
          onSuccess={this.handleLoginSuccess}
          onError={this.handleLoginError}
        />,
        logout: <Logout />,
        profile: <Profile />,
      }
    );
  }
}
