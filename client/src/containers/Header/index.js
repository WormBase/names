import React from 'react';
import PropTypes from 'prop-types';

import {
  AppBar,
  Toolbar,
  Typography,
  withStyles,
} from '../../components/elements';

import { SearchBox } from '../Search';

import { Route, Link } from 'react-router-dom';
import Logo from './Logo';
import NavBar from './NavBar';
import { useEntityTypes } from '../Entity';

const Header = (props) => {
  const { classes } = props;
  const { getEntityType } = useEntityTypes();

  const entity_type_searchbox = React.forwardRef((props, ref) => {
    const entityType = props.match.params.entityType;

    return getEntityType(entityType) ? (
      <SearchBox
        entityType={entityType}
        enableEntityTypeSelect={true}
        classes={{
          root: classes.searchBox,
        }}
        ref={ref}
      />
    ) : null;
  });

  return (
    <div>
      <AppBar position="static" className={classes.root}>
        <Toolbar className={classes.toolbar}>
          <Link to="/">
            <Logo />
          </Link>
          <div className={classes.title}>
            <Link to="/">
              <Typography variant="h6" color="inherit">
                Names Service
              </Typography>
            </Link>
          </div>
          {props.isAuthenticated ? (
            <Route path="/:entityType" component={entity_type_searchbox} />
          ) : null}
          {props.children}
        </Toolbar>
      </AppBar>
    </div>
  );
};

Header.propTypes = {
  classes: PropTypes.object.isRequired,
  isAuthenticated: PropTypes.bool,
};

const styles = (theme) => ({
  root: {},
  toolbar: {
    minHeight: theme.spacing(4),
    [theme.breakpoints.down('sm')]: {
      justifyContent: 'space-between',
      flexWrap: 'wrap',
      padding: `0 0 ${theme.spacing(1)}px 0`,
    },
  },
  title: {
    flexGrow: 1,
    '& a': {
      textDecoration: 'initial',
      color: theme.palette.common.white,
    },
  },
  searchBox: {
    [theme.breakpoints.down('sm')]: {
      order: 10,
      width: '100%',
    },
    margin: `0px ${theme.spacing(2)}px`,
  },
});

export default withStyles(styles)(Header);

export { NavBar };
