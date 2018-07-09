import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Route , Link, Redirect, Switch } from 'react-router-dom';
import 'typeface-roboto';
import { withStyles, Page, } from './components/elements';
import Header, { NavBar } from './containers/Header';
import Authenticate, { ProfileButton } from './containers/Authenticate';
import Footer from './containers/Footer';
import Gene, { GeneProfile, GeneCreate } from './containers/Gene';
import logo from './logo.svg';
import wormbaseLogo from './logo_wormbase_solid.svg';
import './App.css';


class App extends Component {

  render() {
    return (
      <Authenticate>
        {
          ({isAuthenticated, user, login, profile}) => (
            <div className={this.props.classes.root}>
              <Header>
                <ProfileButton name={user.name}/>
              </Header>
              {
                isAuthenticated ? [
                  <NavBar key="nav-bar" />,
                  <div key="content" className={this.props.classes.content}>
                    <Route exact path="/" component={() => <Redirect to="/gene" /> } />
                    <Route exact path="/gene" component={() => <Gene />} />
                    <Route path="/gene" component={({match}) => (
                      <Switch>
                        <Route path={`${match.url}/new`} component={() => <GeneCreate />} />
                        <Route path={`${match.url}/id/:id`} component={({match}) => <GeneProfile wbId={match.params.id} />} />
                        <Route path={`${match.url}/merge`} component={() => <Page>form to merge two genes</Page>} />
                        <Route path={`${match.url}/split`} component={() => <Page>form to split a gene</Page>} />
                      </Switch>
                    )} />
                    <Route path="/variation" component={() => <Page>Variation page (coming soon ..ish)</Page> } />
                    <Route path="/feature" component={() => <Page>Feature page (coming soon ..ish)</Page> } />
                    <Route path="/me" component={() => profile } />
                  </div>
                ] :
                login
              }
              <Footer />
            </div>
          )
        }
      </Authenticate>
    );
  }
}

App.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    display : 'flex',
    flexDirection: 'column',
    minHeight: '100vh',
  },
  content: {
    flex: '1 0 auto',
    display: 'flex',
    flexDirection: 'column',
  }
});

export default withStyles(styles)(App);
