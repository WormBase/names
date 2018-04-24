import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter } from 'react-router-dom';
import { MuiThemeProvider, theme } from './components/elements';

import './index.css';
import App from './App';

//import registerServiceWorker from './registerServiceWorker';

ReactDOM.render((
  <BrowserRouter>
    <MuiThemeProvider theme={theme}>
      <React.StrictMode>
        <App/>
      </React.StrictMode>
    </MuiThemeProvider>
  </BrowserRouter>
), document.getElementById('root'));
//registerServiceWorker();
