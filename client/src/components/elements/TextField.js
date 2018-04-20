import React from 'react';
import { TextField as MuiTextField, withStyles } from 'material-ui';

const TextField = (props) => (
  <div>
    <MuiTextField {...props} />
  </div>
);

const styles = (theme) => ({
  root: {
    marginBottom: theme.spacing.unit * 2,
  },
});

export default withStyles(styles)(TextField);

export {
  TextField,
};
