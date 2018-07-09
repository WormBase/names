import React from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { matchPath } from 'react-router';

import { Toolbar, Tabs, Tab, withStyles } from '../../components/elements';

const NavBar = (props) => {
  const tabs = [
    {
      name: 'Gene',
      value: '/gene',
    },
    {
      name: 'Variation',
      value: '/variation',
    },
    {
      name: 'Feature',
      value: '/feature',
    },
  ];

  const currentTab = tabs.filter((tab) => (
    matchPath(props.location.pathname, {
      path: tab.value,
      exact: false,
      strict: false,
    })
  ))[0];

  return (
      <Tabs
        value={currentTab ? currentTab.value : false}
        onChange={(event, value) => props.history.push(value)}
        centered={true}
        className={props.classes.root}
      >
        {
          tabs.map((tab) => (
            <Tab key={tab.name} label={tab.name} value={tab.value} />
          ))
        }
      </Tabs>
  );
};

NavBar.propTypes = {
  classes: PropTypes.object.isRequired,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }),
  location: PropTypes.shape({
    pathname: PropTypes.string.isRequired,
  }),
};

const styles = (theme) => ({
  root: {
    backgroundColor: theme.palette.background.default,
  },
});

export default withStyles(styles)(withRouter(NavBar));
