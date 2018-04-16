import { MuiThemeProvider, createMuiTheme } from 'material-ui/styles';

import blue from 'material-ui/colors/blue';
import yellow from 'material-ui/colors/yellow';
import red from 'material-ui/colors/red';

export {
  AppBar,
  ToolBar
} from 'material-ui';

export const colors = {
  blue,
  yellow,
  red,
};
// All the following keys are optional.
// We try our best to provide a great default value.
export const theme = createMuiTheme({
  palette: {
    primary: blue,
    secondary: yellow,
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
}
