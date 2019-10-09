import React, { useContext } from 'react';
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
import { EntityTypesContext } from '../Entity';

const Header = (props) => {
  const { classes } = props;
  const entityTypesMap = useContext(EntityTypesContext);

  return (
    <div>
      <AppBar position="static" className={classes.root}>
        <Toolbar className={classes.toolbar}>
          <Link to="/">
            <Logo />
          </Link>
          <div className={classes.title}>
            <Link to="/">
              <Typography variant="title" color="inherit">
                Names Service
              </Typography>
            </Link>
          </div>
          {props.isAuthenticated ? (
            <Route
              path="/:entityType"
              component={({ match }) => {
                const entityType = match.params.entityType;

                return entityTypesMap.has(entityType) ? (
                  <SearchBox
                    entityType={entityType}
                    enableEntityTypeSelect={true}
                    classes={{
                      root: classes.searchBox,
                    }}
                  />
                ) : null;
              }}
            />
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
    minHeight: theme.spacing.unit * 4,
    [theme.breakpoints.down('sm')]: {
      justifyContent: 'space-between',
      flexWrap: 'wrap',
      padding: 0,
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
    top: theme.spacing.unit,
    [theme.breakpoints.down('sm')]: {
      order: 10,
      width: '100%',
    },
    margin: `0px ${theme.spacing.unit * 2}px`,
  },
});

export default withStyles(styles)(Header);

export { NavBar };
