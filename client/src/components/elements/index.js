import blue from '@material-ui/core/colors/blue';
import lightBlue from '@material-ui/core/colors/lightBlue';
import yellow from '@material-ui/core/colors/yellow';
import red from '@material-ui/core/colors/red';

import {
  MuiThemeProvider,
  createMuiTheme,
  withStyles,
  Dialog as MuiDialog,
  withMobileDialog,
} from '@material-ui/core';

export {
  AppBar,
  Button,
  Chip,
  CircularProgress,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Icon,
  InputAdornment,
  MenuItem,
  Paper,
  Snackbar,
  Toolbar,
  Typography,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@material-ui/core';

export { default as DocumentTitle } from 'react-document-title';

export { default as TextField} from './TextField';
export { default as SpeciesSelect } from './SpeciesSelect';
export { default as BiotypeSelect } from './BiotypeSelect';
export { Page, PageLeft, PageMain, PageRight } from './Page';
export { default as Timestamp } from './Timestamp';
export { default as SimpleListPagination } from './SimpleListPagination';
export { default as AutocompleteBase } from './AutocompleteBase';

export const Dialog = withMobileDialog()(withStyles((theme) => ({
  paper: {
    minWidth: '50%',
    marginTop: theme.spacing.unit * -10,
    [theme.breakpoints.down('sm')]:{
      marginTop: 0,
    }
  },
}))(MuiDialog));

export const colors = {
  blue,
  lightBlue,
  yellow,
  red,
};
// All the following keys are optional.
// We try our best to provide a great default value.
export const theme = createMuiTheme({
  palette: {
    primary: {
      main: blue[600],
    },
    secondary: {
      main: yellow['A700'],
    },
    error: red,
    // Used by `getContrastText()` to maximize the contrast between the background and
    // the text.
    contrastThreshold: 3,
    // Used to shift a color's luminance by approximately
    // two indexes within its tonal palette.
    // E.g., shift from Red 500 to Red 300 or Red 700.
    tonalOffset: 0.2,
  },
});

export {
  MuiThemeProvider,
  withStyles,
}
