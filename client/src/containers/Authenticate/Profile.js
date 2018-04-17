import React from 'react';
import PropTypes from 'prop-types';
import { Button, withStyles } from '../../components/elements';

const Profile = (props) => {
  return (
    <div className={props.classes.root}>
      <h2>{props.name}</h2>
      <span>{props.email}</span>
      <div className={props.classes.logout}>
        {props.children}
      </div>
    </div>
  );
};

Profile.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string.isRequired,
  email: PropTypes.string.isRequired,
  onLogout: PropTypes.func.isRequired,
  children: PropTypes.element,
};

const styles = (theme) => ({
  root: {
    flexGrow: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
  },
  logout: {
    margin: theme.spacing.unit * 6,
  },
});

export default withStyles(styles)(Profile);
