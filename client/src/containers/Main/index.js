import React, { useContext } from 'react';
import PropTypes from 'prop-types';
import { Route, Switch, matchPath } from 'react-router-dom';
import 'typeface-roboto';
import {
  withStyles,
  MuiThemeProvider,
  CircularProgress,
  Page,
  DocumentTitle,
  ErrorBoundary,
  NotFound,
} from '../../components/elements';
import Header, { NavBar } from '../Header';
import {
  AuthorizationContext,
  ProfileButton,
  TokenMgmt,
  Login,
  Logout,
  Profile,
} from '../Authenticate';
import Footer from '../Footer';
import Home from '../Home';
import { GeneDirectory, GeneProfile, GeneCreate } from '../Gene';
import {
  EntityDirectory,
  EntityProfile,
  EntityCreate,
  useEntityTypes,
} from '../Entity';

// import './App.css';

function Main({ classes }) {
  const {
    isAuthenticated,
    errorMessage,
    user,
    handleLogin,
    handleLogout,
  } = useContext(AuthorizationContext);
  const { entityTypesAll, getEntityType } = useEntityTypes();
  return (
    <div className={classes.root}>
      <Header isAuthenticated={isAuthenticated}>
        <ProfileButton name={user.name} />
      </Header>
      {isAuthenticated === undefined ||
      (isAuthenticated && entityTypesAll.length === 0) ? (
        <div className={classes.content}>
          <Page>
            <CircularProgress />
          </Page>
        </div>
      ) : isAuthenticated ? (
        <React.Fragment>
          <NavBar key="nav-bar" />
          <div key="content" className={classes.content}>
            <ErrorBoundary>
              <Switch>
                <Route exact path="/" component={() => <Home />} />
                <Route
                  path="/me"
                  component={() => (
                    <DocumentTitle title="Your profile">
                      <Profile {...user}>
                        <TokenMgmt />
                        <br />
                        <Logout onLogout={handleLogout} />
                      </Profile>
                    </DocumentTitle>
                  )}
                />
                <Route
                  path={entityTypesAll.map(({ path }) => path)}
                  component={({ match }) => {
                    const entityType = matchPath(match.url, {
                      path: '/:entityType',
                    }).params.entityType;

                    let Directory, Create, Profile;
                    switch (entityType) {
                      case 'gene':
                        [Directory, Create, Profile] = [
                          GeneDirectory,
                          GeneCreate,
                          GeneProfile,
                        ];
                        break;
                      default:
                        [Directory, Create, Profile] = [
                          EntityDirectory,
                          EntityCreate,
                          EntityProfile,
                        ];
                    }
                    return (
                      <MuiThemeProvider
                        theme={
                          getEntityType(entityType) &&
                          getEntityType(entityType).theme
                        }
                      >
                        <Switch>
                          <Route
                            path={`${match.url}`}
                            exact={true}
                            component={() => (
                              <Directory entityType={entityType} />
                            )}
                          />
                          <Route
                            path={`${match.url}/new`}
                            component={() => <Create entityType={entityType} />}
                          />
                          <Route
                            path={`${match.url}/id/:id`}
                            component={({ match }) => (
                              <Profile
                                wbId={match.params.id}
                                entityType={entityType}
                              />
                            )}
                          />
                          <Route component={NotFound} />
                        </Switch>
                      </MuiThemeProvider>
                    );
                  }}
                />
                <Route component={NotFound} />
              </Switch>
            </ErrorBoundary>
          </div>
        </React.Fragment>
      ) : (
        <Login onSignIn={handleLogin} errorMessage={errorMessage} />
      )}
      <Footer />
    </div>
  );
}

Main.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    display: 'flex',
    flexDirection: 'column',
    minHeight: '100vh',
  },
  content: {
    flex: '1 0 auto',
    display: 'flex',
    flexDirection: 'column',
  },
});

export default withStyles(styles)(Main);
