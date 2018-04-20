import React from 'react';
import PropTypes from 'prop-types';
import { AppBar, Toolbar, Typography, withStyles } from '../../components/elements';
import Logo from './Logo';
import NavBar from './NavBar';


const Header = (props) => {
  const {classes} = props;
  return (
    <div>
      <AppBar position="static" className={classes.root}>
        <Toolbar className={classes.toolbar}>
          <Logo />
          <Typography variant="title" color="inherit" className={classes.title}>
            WormBase Name Server
          </Typography>
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
  },
});

export default withStyles(styles)(Header);

export {
  NavBar,
};