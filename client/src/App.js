import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { GoogleOAuthProvider } from '@react-oauth/google';
import Authenticate from './containers/Authenticate';
import { EntityTypesContextProvider } from './containers/Entity';
import Main from './containers/Main';
import { theme, MuiThemeProvider } from './components/elements';

export default () => (
  <GoogleOAuthProvider clientId={process.env.REACT_APP_GOOGLE_OAUTH_CLIENT_ID}>
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
