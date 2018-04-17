import React, { Component } from 'react';
import Login from './Login';
import Logout from './Logout';
import Profile from './Profile';
import ProfileButton from './ProfileButton';

export default class Authenticate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      isAuthenticated: false,
      user: {
        name: null,
        email: null,
        id_token: null,
      },
      errorMessage: JSON.stringify({a: 100}, undefined, 2),
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

  render() {
    console.log(this.state);
    return this.props.children(
      {
        isAuthenticated: this.state.isAuthenticated,
        user: {...this.state.user},
        login: <Login
          onSuccess={this.handleLoginSuccess}
          onError={this.handleLoginError}
          errorMessage={this.state.errorMessage}
        />,
        logout: <Logout />,
        profile: <Profile />,
      }
    );
  }
}

export {
  ProfileButton,
}
