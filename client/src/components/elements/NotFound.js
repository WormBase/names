import React from 'react';
import PropTypes from 'prop-types';
import { Typography, withStyles } from '@material-ui/core';

import { useTitle } from '../../hooks/useTitle';

const NotFound = (props) => {
  const { classes } = props;

  useTitle('Not found');

  return (
    <div className={classes.root}>
      <Typography variant="h3" gutterBottom>
        Not Found
      </Typography>
      {props.children}
    </div>
  );
};

NotFound.propTypes = {
  children: PropTypes.element,
};

const styles = (theme) => ({
  root: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    margin: theme.spacing(5),
    [theme.breakpoints.down('sm')]: {
      margin: `${theme.spacing(5)}px 0`,
    },
    '& > *': {
      marginBottom: theme.spacing(2),
    },
  },
});

export default withStyles(styles)(NotFound);
