import React from 'react';
import icon from './icon.svg';
import { withStyles } from '../../components/elements';

const Logo = (props) => {
  const { classes } = props;
  return (
    <div className={classes.root}>
      <img src={icon} alt="WormBase" />
    </div>
  );
};

const styles = (theme) => ({
  root: {
    width: theme.spacing(4),
    height: theme.spacing(4),
    margin: theme.spacing(1),
  },
});

export default withStyles(styles)(Logo);
