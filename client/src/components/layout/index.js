import React from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button } from '../../components/elements';

const BasicPage = (props) => {
  const {classes} = props;
  return (
    <div className={classes.root}>
      {props.children}
    </div>
  );
}

BasicPage.propTypes = {
  classes: PropTypes.object.isRequired,
  children: PropTypes.element,
};

const styles = (theme) => ({
  root: {
    margin: theme.spacing.unit * 8,
  },
});

export default withStyles(styles)(BasicPage);
