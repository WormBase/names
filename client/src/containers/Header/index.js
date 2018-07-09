import React from 'react';
import PropTypes from 'prop-types';
import { AppBar, Toolbar, Typography, withStyles } from '../../components/elements';
import { Link } from 'react-router-dom';
import Logo from './Logo';
import NavBar from './NavBar';


const Header = (props) => {
  const {classes} = props;
  return (
    <div>
      <AppBar position="static" className={classes.root}>
        <Toolbar className={classes.toolbar}>
          <Link to="/">
            <Logo />
          </Link>
          <div className={classes.title}>
            <Link to="/" >
              <Typography variant="title" color="inherit" >
                WormBase Name Server
              </Typography>
            </Link>
          </div>

          {props.children}
        </Toolbar>
      </AppBar>
    </div>
  );
};

Header.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
  },
  toolbar: {
    minHeight: theme.spacing.unit * 4
  },
  title: {
    flexGrow: 1,
    '& a': {
      textDecoration: 'initial',
      color: 'initial',
    },
  },
});

export default withStyles(styles)(Header);

export {
  NavBar,
};
