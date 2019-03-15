import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Route, Redirect, Switch } from 'react-router-dom';
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
import Authenticate, { ProfileButton } from './containers/Authenticate';
import Footer from './containers/Footer';
import { GeneDirectory, GeneProfile, GeneCreate } from './containers/Gene';
import './App.css';

class App extends Component {
  render() {
    return (
      <Authenticate>
        {({ isAuthenticated, user, login, profile, authorizedFetch }) => (
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
                      <Route
                        exact
                        path="/"
                        component={() => <Redirect to="/gene" />}
                      />
                      <Route
                        exact
                        path="/gene"
                        component={() => (
                          <DocumentTitle title="Gene index">
                            <GeneDirectory authorizedFetch={authorizedFetch} />
                          </DocumentTitle>
                        )}
                      />
                      <Route
                        path="/gene"
                        component={({ match }) => (
                          <Switch>
                            <Route
                              path={`${match.url}/new`}
                              component={() => (
                                <DocumentTitle title={`Create a gene`}>
                                  <GeneCreate
                                    authorizedFetch={authorizedFetch}
                                  />
                                </DocumentTitle>
                              )}
                            />
                            <Route
                              path={`${match.url}/id/:id`}
                              component={({ match }) => (
                                <DocumentTitle
                                  title={`Gene ${match.params.id}`}
                                >
                                  <GeneProfile
                                    wbId={match.params.id}
                                    authorizedFetch={authorizedFetch}
                                  />
                                </DocumentTitle>
                              )}
                            />
                            <Route component={NotFound} />
                          </Switch>
                        )}
                      />
                      <Route
                        path="/variation"
                        component={() => (
                          <DocumentTitle title="Variation index">
                            <Page>Variation page (coming soon ..ish)</Page>
                          </DocumentTitle>
                        )}
                      />
                      <Route
                        path="/feature"
                        component={() => (
                          <DocumentTitle title="Feature index">
                            <Page>Feature page (coming soon ..ish)</Page>
                          </DocumentTitle>
                        )}
                      />
                      <Route
                        path="/me"
                        component={() => (
                          <DocumentTitle title="My profile">
                            {profile}
                          </DocumentTitle>
                        )}
                      />
                      <Route component={NotFound} />
                    </Switch>
                  </ErrorBoundary>
                </div>,
              ]
            ) : (
              login
            )}
            <Footer />
          </div>
        )}
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
