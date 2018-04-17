import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Route , Link } from 'react-router-dom';
import 'typeface-roboto';
import { withStyles } from './components/elements';
import Header, { NavBar } from './containers/Header';
import Authenticate, { ProfileButton } from './containers/Authenticate';
import Footer from './containers/Footer';
import {startMock, stopMock} from './mock';
import logo from './logo.svg';
import wormbaseLogo from './logo_wormbase_solid.svg';
import './App.css';


class App extends Component {
  componentDidMount() {
    startMock();
    fetch('/aaaa').then(function(data) {
      console.log('got data', data);
    });
  }

  componentWillUnmount() {
    stopMock();
  }

  render() {
    return (
      <Authenticate>
        {
          ({isAuthenticated, user, login, profile}) => (
            <div className={this.props.classes.root}>
              <Header>
                <ProfileButton name={user.name}/>
              </Header>
              <div className={this.props.classes.content}>
              {
                isAuthenticated ? [
                  <NavBar />,
                  <Route exact path="/" component={() => 'home page' } />,
                  <Route path="/gene" component={({match}) => ([
                    <Route path="/" exact={false} strict={false} component={() => (
                      <ul>
                        <li><Link to="/">Home</Link></li>
                        <li><Link to="/gene/new">Create a new gene</Link></li>
                        <li><Link to="/gene/id/WB1">Edit an exiting gene (example)</Link></li>
                        <li><Link to="/gene/merge">Merge two genes</Link></li>
                        <li><Link to="/gene/split">Split a gene</Link></li>
                      </ul>
                    )} />,
                    <Route path={`${match.url}/new`} component={() => 'form to create new gene'} />,
                    <Route path={`${match.url}/id/:id`} component={() => 'form edit an existing new gene'} />,
                    <Route path={`${match.url}/merge`} component={() => 'form to merge two genes'} />,
                    <Route path={`${match.url}/split`} component={() => 'form to split a gene'} />,
                  ])} />,
                  <Route path="/variation" component={() => 'variation page' } />,
                  <Route path="/me" component={() => profile } />,
                ] :
                login
              }
              </div>
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
