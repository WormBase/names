import React from 'react';
import PropTypes from 'prop-types';
import { Button, withStyles } from '../../components/elements';
import { Link } from 'react-router-dom';
import PersonIcon from '@material-ui/icons/Person';

const my_profile_link = React.forwardRef(function(props, ref) {
  return <Link to="/me" {...props} ref={ref} />;
});

const ProfileButton = (props) => {
  return props.name ? (
    <Button component={my_profile_link} className={props.classes.button}>
      <PersonIcon className={props.classes.icon} />
      Hi, {props.name.split(/\s+/)[0]}
    </Button>
  ) : null;
};

ProfileButton.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  button: {
    color: theme.palette.common.white,
    textTransform: 'none',
  },
  icon: {
    marginRight: theme.spacing(1),
  },
});

export default withStyles(styles)(ProfileButton);
