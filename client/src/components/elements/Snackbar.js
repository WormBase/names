import MuiSnackbar from '@material-ui/core/Snackbar';
import { withStyles } from '@material-ui/core/styles';

const styles = (theme) => ({
  root: {
    left: 0,
    right: 0,
    transform: 'unset',
  },
});

export default withStyles(styles)(MuiSnackbar);
