import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Route, Redirect, Switch, matchPath } from 'react-router-dom';
import 'typeface-roboto';
import {
  withStyles,
  CircularProgress,
  Page,
  DocumentTitle,
  ErrorBoundary,
  NotFound,
} from './components/elements';
import Header, { NavBar } from './containers/Header';
import Authenticate, {
  AuthorizationContext,
  ProfileButton,
  Login,
  Logout,
  Profile,
} from './containers/Authenticate';
import Footer from './containers/Footer';
import Home from './containers/Home';
import { GeneDirectory, GeneProfile, GeneCreate } from './containers/Gene';
import {
  EntityDirectory,
  EntityProfile,
  EntityCreate,
} from './containers/Entity';
import { ENTITY_TYPE_PATHS } from '../src/utils/entityTypes';
// import {
//   Directory as VariationDirectory,
//   Create as VariationCreate,
//   Profile as VariationProfile,
// } from './containers/Variation';
import './App.css';

class App extends Component {
  render() {
    return (
      <Authenticate>
        <AuthorizationContext.Consumer>
          {({
            isAuthenticated,
            errorMessage,
            user,
            handleLogin,
            handleLogout,
          }) => (
            <div className={this.props.classes.root}>
              <Header isAuthenticated={isAuthenticated}>
                <ProfileButton name={user.name} />
              </Header>
              {isAuthenticated === undefined ? (
                <div className={this.props.classes.content}>
                  <Page>
                    <CircularProgress />
                  </Page>
                </div>
              ) : isAuthenticated ? (
                [
                  <NavBar key="nav-bar" />,
                  <div key="content" className={this.props.classes.content}>
                    <ErrorBoundary>
                      <Switch>
                        <Route exact path="/" component={() => <Home />} />
                        <Route
                          path="/me"
                          component={() => (
                            <DocumentTitle title="Your profile">
                              <Profile {...user}>
                                <Logout onLogout={handleLogout} />
                              </Profile>
                            </DocumentTitle>
                          )}
                        />
                        <Route
                          path={ENTITY_TYPE_PATHS}
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
                                  component={() => (
                                    <Create entityType={entityType} />
                                  )}
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
                            );
                          }}
                        />
                        <Route component={NotFound} />
                      </Switch>
                    </ErrorBoundary>
                  </div>,
                ]
              ) : (
                <Login onSignIn={handleLogin} errorMessage={errorMessage} />
              )}
              <Footer />
            </div>
          )}
        </AuthorizationContext.Consumer>
      </Authenticate>
    );
  }
}

App.propTypes = {
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

export default withStyles(styles)(App);
