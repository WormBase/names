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
  Divider,
  Icon,
  IconButton,
  InputAdornment,
  MenuItem,
  Paper,
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

export { default as AjaxDialog } from './AjaxDialog';
export { default as AutocompleteBase } from './AutocompleteBase';
export { default as BaseForm } from './BaseForm';
export { default as BiotypeSelect } from './BiotypeSelect';
export { default as ErrorBoundary } from './ErrorBoundary';
export { default as SimpleAjax } from './SimpleAjax';
export * from './Layout';
export { default as NotFound } from './NotFound';
export { Page, PageLeft, PageMain } from './Page';
export * from './ProgressButton';
export { default as ProgressButton } from './ProgressButton';
export { default as SimpleListPagination } from './SimpleListPagination';
export { default as Snackbar } from './Snackbar';
export { default as SnackbarContent } from './SnackbarContent';
export { default as SpeciesSelect } from './SpeciesSelect';
export { default as TextField } from './TextField';
export { default as Timestamp } from './Timestamp';
export { default as Humanize } from './Humanize';
export { default as ValidationError } from './ValidationError';

export const Dialog = withMobileDialog()(
  withStyles((theme) => ({
    paper: {
      minWidth: '50%',
      marginTop: theme.spacing.unit * -10,
      [theme.breakpoints.down('sm')]: {
        marginTop: 0,
      },
    },
  }))(MuiDialog)
);

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
      main: blue[700],
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

export { MuiThemeProvider, withStyles };
