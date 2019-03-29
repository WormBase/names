import React from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { matchPath } from 'react-router';

import { Tabs, Tab, withStyles } from '../../components/elements';
import { capitalize } from '../../utils/format';
import { ENTITY_TYPES } from '../../utils/entityTypes';

const NavBar = (props) => {
  const currentTab = ENTITY_TYPES.filter((entityType) =>
    matchPath(props.location.pathname, {
      path: `/${entityType}`,
      exact: false,
      strict: false,
    })
  )[0];

  return (
    <Tabs
      value={currentTab ? currentTab : false}
      onChange={(event, value) => props.history.push(`/${value}`)}
      centered={true}
      className={props.classes.root}
    >
      {ENTITY_TYPES.map((entityType) => (
        <Tab
          key={entityType}
          label={capitalize(entityType)}
          value={entityType}
        />
      ))}
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
