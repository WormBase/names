import React, { useContext } from 'react';
import PropTypes from 'prop-types';
import { withRouter, Link } from 'react-router-dom';
import { matchPath } from 'react-router';

import {
  Tabs,
  Tab,
  MuiThemeProvider,
  withStyles,
} from '../../components/elements';
import { EntityTypesContext } from '../Entity';
import { capitalize } from '../../utils/format';

const NavBar = (props) => {
  const entityTypesMap = useContext(EntityTypesContext);
  const allTabs = [
    { entityType: 'home', path: '/' },
    ...[...entityTypesMap].map(([, entityTypeConfig]) => entityTypeConfig),
  ];
  const { entityType: currentTab } =
    allTabs
      .filter(({ path }) =>
        matchPath(props.location.pathname, {
          path: path,
          exact: false,
          strict: false,
        })
      )
      .slice(-1)[0] || {};

  return (
    <MuiThemeProvider
      theme={
        entityTypesMap.get(currentTab) && entityTypesMap.get(currentTab).theme
      }
    >
      <Tabs
        value={currentTab ? currentTab : false}
        centered={true}
        className={props.classes.root}
      >
        {allTabs.map(({ entityType, displayName, path }) => (
          <Tab
            key={entityType}
            label={displayName || capitalize(entityType)}
            value={entityType}
            style={{
              color:
                entityTypesMap.get(entityType) &&
                entityTypesMap.get(entityType).theme
                  ? entityTypesMap.get(entityType).theme.palette.secondary.dark
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
