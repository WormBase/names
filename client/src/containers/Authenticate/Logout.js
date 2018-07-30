import React from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { Button } from '../../components/elements';

const Logout = (props) => {
  return (
    <Button
      variant="raised"

      onClick={() => {
        props.onLogout();
        props.history.push('/');
      }}
    >
      Logout
    </Button>
  );
}

Logout.propTypes = {
  onLogout: PropTypes.func.isRequired,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }),
};

export default withRouter(Logout);
