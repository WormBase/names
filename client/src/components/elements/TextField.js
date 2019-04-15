import React from 'react';
import { TextField as MuiTextField, withStyles } from '@material-ui/core';

const TextField = (props) => <MuiTextField {...props} />;

const styles = (theme) => ({
  root: {
    marginBottom: theme.spacing.unit * 2,
    display: 'block',
  },
});

export default withStyles(styles)(TextField);

export { TextField };
