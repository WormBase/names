import React from 'react';
import PropTypes from 'prop-types';
import { withRouter, Link } from 'react-router-dom';
import { matchPath } from 'react-router';

import {
  Tabs,
  Tab,
  MuiThemeProvider,
  withStyles,
} from '../../components/elements';
import { capitalize } from '../../utils/format';
import { ENTITY_TYPES, getEntityTypeTheme } from '../../utils/entityTypes';

const ALL_TABS = [{ entityType: 'home', path: '/' }, ...ENTITY_TYPES];

const NavBar = (props) => {
  const { entityType: currentTab } =
    ALL_TABS.filter(({ path }) =>
      matchPath(props.location.pathname, {
        path: path,
        exact: false,
        strict: false,
      })
    ).slice(-1)[0] || {};

  return (
    <MuiThemeProvider theme={getEntityTypeTheme(currentTab)}>
      <Tabs
        value={currentTab ? currentTab : false}
        centered={true}
        className={props.classes.root}
      >
        {ALL_TABS.map(({ entityType, path }) => (
          <Tab
            key={entityType}
            label={capitalize(entityType)}
            value={entityType}
            style={{
              color: getEntityTypeTheme(entityType)
                ? getEntityTypeTheme(entityType).palette.secondary.dark
                : '#000',
            }}
            component={(props) => <Link {...props} to={path} />}
          />
        ))}
      </Tabs>
    </MuiThemeProvider>
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
