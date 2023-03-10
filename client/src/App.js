import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import Authenticate from './containers/Authenticate';
import { EntityTypesContextProvider } from './containers/Entity';
import Main from './containers/Main';
import { theme, MuiThemeProvider } from './components/elements';

export default () => (
  <GoogleOAuthProvider clientId="514830196757-8464k0qoaqlb4i238t8o6pc6t9hnevv0.apps.googleusercontent.com">
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
  </GoogleOAuthProvider>
);
