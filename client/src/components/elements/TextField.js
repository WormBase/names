import React from 'react';
import { TextField as MuiTextField, withStyles } from '@material-ui/core';

const TextField = (props) => <MuiTextField variant="outlined" {...props} />;

const styles = (theme) => ({
  root: {
    margin: `${theme.spacing(1)}px ${theme.spacing(0.25)}px`,
    // display: 'block',
  },
});

export default withStyles(styles)(TextField);

export { TextField };
