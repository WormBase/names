import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import Authenticate from './containers/Authenticate';
import { EntityTypesContextProvider } from './containers/Entity';
import Main from './containers/Main';
import { theme, MuiThemeProvider } from './components/elements';

export default () => (
  <React.StrictMode>
    <BrowserRouter>
      <Authenticate>
        <EntityTypesContextProvider>
          <MuiThemeProvider theme={theme}>
            <Main />
          </MuiThemeProvider>
        </EntityTypesContextProvider>
      </Authenticate>
    </BrowserRouter>
  </React.StrictMode>
);
