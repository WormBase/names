import React from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core';

function NoData({ classes, children }) {
  return <span className={classes.root}>{children}</span>;
}

NoData.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: { color: theme.palette.text.hint },
});

export default withStyles(styles)(NoData);
