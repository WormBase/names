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
                        path="/me"
                        component={() => (
                          <DocumentTitle title="Your profile">
                            {profile}
                          </DocumentTitle>
                        )}
                      />
                      <Route
                        path="/:entityType"
                        component={({ match }) => {
                          let Directory, Create, Profile;
                          switch (match.params.entityType) {
                            case 'gene':
                              [Directory, Create, Profile] = [
                                GeneDirectory,
                                GeneCreate,
                                GeneProfile,
                              ];
                              break;
                            // case 'variation':
                            //   [Directory, Create, Profile] = [
                            //     VariationDirectory,
                            //     VariationCreate,
                            //     VariationProfile,
                            //   ];
                            //   break;
                            default:
                              return <NotFound />;
                          }
                          return (
                            <Switch>
                              <Route
                                path={`${match.url}`}
                                exact={true}
                                component={() => (
                                  <Directory
                                    authorizedFetch={authorizedFetch}
                                  />
                                )}
                              />
                              <Route
                                path={`${match.url}/new`}
                                component={() => (
                                  <Create authorizedFetch={authorizedFetch} />
                                )}
                              />
                              <Route
                                path={`${match.url}/id/:id`}
                                component={({ match }) => (
                                  <Profile
                                    wbId={match.params.id}
                                    authorizedFetch={authorizedFetch}
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
