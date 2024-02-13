import {
  blueGrey as primaryColor,
  blueGrey as secondaryColor,
  red,
} from '@material-ui/core/colors';

import {
  MuiThemeProvider,
  createTheme,
  withStyles,
  Dialog as MuiDialog,
  withMobileDialog,
} from '@material-ui/core';

export {
  AppBar,
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

export { default as AjaxDialog } from './AjaxDialog';
export { default as AutocompleteBase } from './AutocompleteBase';
export { default as AutocompleteChip } from './AutocompleteChip';
export { default as AutocompleteSuggestion } from './AutocompleteSuggestion';
export { default as BaseForm } from './BaseForm';
export { default as BiotypeSelect } from './BiotypeSelect';
export { default as Button } from './Button';
export { default as DocumentTitle } from './DocumentTitle';
export { default as EntityTypeSelect } from './EntityTypeSelect';
export { default as ErrorBoundary } from './ErrorBoundary';
export { default as SimpleAjax } from './SimpleAjax';
export { default as NoData } from './NoData';
export { default as NotFound } from './NotFound';
export { Page, PageLeft, PageMain } from './Page';
export * from './ProgressButton';
export { default as ProgressButton } from './ProgressButton';
export { default as SimpleListPagination } from './SimpleListPagination';
export { default as Snackbar } from './Snackbar';
export { default as SnackbarContent } from './SnackbarContent';
export { default as SpeciesSelect } from './SpeciesSelect';
export { default as TextArea } from './TextArea';
export { default as TextField } from './TextField';
export { default as Timestamp } from './Timestamp';
export { default as Humanize } from './Humanize';
export { default as ValidationError } from './ValidationError';

export { default as useClipboard } from './useClipboard';

export const Dialog = withMobileDialog()(
  withStyles((theme) => ({
    paper: {
      minWidth: '50%',
      marginTop: theme.spacing(-10),
      [theme.breakpoints.down('sm')]: {
        marginTop: 0,
      },
    },
  }))(MuiDialog)
);

// export const colors = {
//   blue,
//   lightBlue,
//   yellow,
//   red,
// };
// All the following keys are optional.
// We try our best to provide a great default value.
export const theme = createTheme({
  palette: {
    primary: {
      main: primaryColor[700],
    },
    secondary: {
      main: secondaryColor['A700'],
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

export { MuiThemeProvider, createTheme, withStyles };
