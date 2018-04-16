import React from 'react';
import PropTypes from 'prop-types';
import { Toolbar, Tabs, Tab, withStyles } from '../../components/elements';

const NavBar = (props) => {
  return (
      <Tabs
        value={0}
        onChange={this.handleChange}
        centered={true}
        className={props.classes.root}
      >
        <Tab label="Gene" />
        <Tab label="Variation" />
        <Tab label="Feature" />
      </Tabs>
  );
};

NavBar.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    //backgroundColor: theme.palette.primary.main,
    backgroundColor: theme.palette.background.default,
  },
});

export default withStyles(styles)(NavBar);
