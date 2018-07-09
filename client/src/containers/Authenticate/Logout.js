import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Button, withStyles } from '../../components/elements';

const Logout = (props) => {
  return (
    <Button
      variant="raised"

      onClick={() => props.onLogout()}
    >
      Logout
    </Button>
  );
}

Logout.propTypes = {
  onLogout: PropTypes.func.isRequired,
};

export default Logout;
