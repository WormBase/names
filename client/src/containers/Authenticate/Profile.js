import React from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '../../components/elements';

const Profile = (props) => {
  return (
    <div className={props.classes.root}>
      <h2>{props.name}</h2>
      <span>ID: {props.id}</span>
      <br />
      <span>Email: {props.email}</span>
      <div className={props.classes.actions}>{props.children}</div>
    </div>
  );
};

Profile.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string.isRequired,
  email: PropTypes.string.isRequired,
  id: PropTypes.string.isRequired,
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
  actions: {
    margin: theme.spacing(4),
  },
});

export default withStyles(styles)(Profile);
