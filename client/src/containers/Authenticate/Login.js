import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Button, withStyles } from '../../components/elements';

class Login extends Component {

  // adapted from https://developers.google.com/identity/sign-in/web/build-button
  initializeSignIn = (element) => {
    const gapi = window.gapi;
    gapi.load('auth2', () => {
      // Retrieve the singleton for the GoogleAuth library and set up the client.
      const auth2 = gapi.auth2.init({
        client_id: '514830196757-8464k0qoaqlb4i238t8o6pc6t9hnevv0.apps.googleusercontent.com',
        cookiepolicy: 'single_host_origin',
        // Request scopes in addition to 'profile' and 'email'
        //scope: 'additional_scope'
      });

      auth2.attachClickHandler(element, {},
        (googleUser) => {
          const googleUserProfile = googleUser.getBasicProfile();
          this.props.onSuccess({
            name: googleUserProfile.getName(),
            email: googleUserProfile.getEmail(),
            id_token: googleUser.getAuthResponse().id_token,
          }, () => {
            auth2.disconnect();
          });
        }, (error) => {
          this.props.onError(error);
        }
      );
    });
  }

  componentDidMount() {
    this.initializeSignIn(this.buttonElement);
  }

  render() {
    const {classes, errorMessage} = this.props;
    return (
      <div className={classes.root}>
        {
          errorMessage ?
            <div className={classes.errorWrapper}>
              Problem signing in:
              <div className={classes.errorMessage}>
               {errorMessage}
              </div>
            </div> :
            null
        }
        <Button
          buttonRef={element => this.buttonElement = element}
          variant="raised"
        >
          Login with Google
        </Button>
      </div>
    );
  }
}

Login.propTypes = {
  classes: PropTypes.object.isRequired,
  onSuccess: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
  errorMessage: PropTypes.string,
};

const styles = (theme) => ({
  root: {
    flexGrow: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.palette.primary.main,
  },
  errorWrapper: {
    minWidth: '50%',
    textAlign: 'center',
  },
  errorMessage: {
    backgroundColor: 'rgba(255, 255, 255, 0.6)',
    margin: theme.spacing.unit * 2,
    padding: theme.spacing.unit * 4,
    color: theme.palette.error.main,
    textAlign: 'left',
    fontFamily: 'monospace',
  },
});

export default withStyles(styles)(Login);
