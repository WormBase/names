import React from 'react';
import PropTypes from 'prop-types';
import { withRouter, Link } from 'react-router-dom';
import { matchPath } from 'react-router';

import { Tabs, Tab, withStyles } from '../../components/elements';
import { useEntityTypes } from '../Entity';
import { capitalize } from '../../utils/format';

const NavBar = (props) => {
  const { entityTypesAll, getEntityType } = useEntityTypes();
  const allTabs = [{ entityType: 'home', path: '/' }, ...entityTypesAll];
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
    <Tabs
      value={currentTab ? currentTab : false}
      centered={true}
      className={props.classes.root}
    >
      {allTabs.map(({ entityType, displayName, path }) => {
        const entity_dir_link = React.forwardRef((props, ref) => (
          <Link {...props} to={path} ref={ref} />
        ));
        return (
          <Tab
            key={entityType}
            label={displayName || capitalize(entityType)}
            value={entityType}
            style={{
              color:
                getEntityType(entityType) && getEntityType(entityType).theme
                  ? getEntityType(entityType).theme.palette.secondary.dark
                  : '#000',
            }}
            component={entity_dir_link}
          />
        );
      })}
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
