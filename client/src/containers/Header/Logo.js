import React from 'react';
import icon from './icon.svg';
import { withStyles } from '../../components/elements'

const Logo = (props) => {
  const {classes} = props;
  return (
    <div className={classes.root}>
      <img src={icon}></img>
    </div>
  );
};

const styles = (theme) => ({
  root: {
    width: theme.spacing.unit * 4,
    height: theme.spacing.unit * 4,
    margin: theme.spacing.unit,
  }
});

export default withStyles(styles)(Logo);
