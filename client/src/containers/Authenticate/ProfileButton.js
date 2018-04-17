import React from 'react';
import PropTypes from 'prop-types';
import { Button, withStyles } from '../../components/elements';
import { Link } from 'react-router-dom';
import PersonIcon from '@material-ui/icons/Person';

const ProfileButton = (props) => {
  return props.name ? (
    <Button
      component={({...props}) => <Link to='/me' {...props} />}
    >
      <PersonIcon className={props.classes.icon} />
      Hello, {props.name}
    </Button>
  ) : null;
};

ProfileButton.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  icon: {
    marginRight: theme.spacing.unit,
  },
});

export default withStyles(styles)(ProfileButton);
