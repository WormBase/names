import { MuiThemeProvider, createMuiTheme, withStyles } from 'material-ui/styles';

import blue from 'material-ui/colors/blue';
import lightBlue from 'material-ui/colors/lightBlue';
import yellow from 'material-ui/colors/yellow';
import red from 'material-ui/colors/red';

export {
  AppBar,
  Button,
  Icon,
  MenuItem,
  Toolbar,
  Typography,
  Tabs,
  Tab,
  TextField,
} from 'material-ui';

//export { default as TextField} from './TextField';
export { default as SpeciesSelect } from './SpeciesSelect';
export { default as BiotypeSelect } from './BiotypeSelect';

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
