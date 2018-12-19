import React from 'react';
import { withStyles } from '../components/elements';

const Footer = (props) => {
  const { classes } = props;
  return (
    <div className={classes.root}>
      For help and questions, please contact us at developers@wormbase.org.
    </div>
  );
};

const styles = (theme) => ({
  root: {
    width: '100%',
    height: theme.spacing.unit * 6,
    textAlign: 'center',
    backgroundColor: theme.palette.grey['50'],
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
  },
});

export default withStyles(styles)(Footer);
