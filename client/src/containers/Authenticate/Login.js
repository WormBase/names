import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Button, withStyles } from '../../components/elements';

class Login extends Component {
  render() {
    const { classes, onSignIn, errorMessage } = this.props;
    return (
      <div className={classes.root}>
        {errorMessage ? (
          <div className={classes.errorWrapper}>
            Problem signing in:
            <div className={classes.errorMessage}>{errorMessage}</div>
          </div>
        ) : null}
        <Button onClick={onSignIn} variant="contained">
          Login with Google
        </Button>
      </div>
    );
  }
}

Login.propTypes = {
  classes: PropTypes.object.isRequired,
  onSignIn: PropTypes.func.isRequired,
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
    margin: theme.spacing(2),
    padding: theme.spacing(4),
    color: theme.palette.error.main,
    textAlign: 'left',
    fontFamily: 'monospace',
  },
});

export default withStyles(styles)(Login);
