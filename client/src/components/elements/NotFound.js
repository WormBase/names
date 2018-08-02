import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Typography, withStyles } from '@material-ui/core';

class NotFound extends Component {
  render() {
    const {classes} = this.props;
    return (
      <div className={classes.root}>
        <Typography variant="display2" gutterBottom>Not Found</Typography>
        {this.props.children}
      </div>
    )
  }
}

NotFound.propTypes = {
  children: PropTypes.element,
};

const styles = (theme) => ({
  root: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    margin: theme.spacing.unit * 5,
    [theme.breakpoints.down('sm')]: {
      margin: `${theme.spacing.unit * 5}px 0`,
    },
    '& > *': {
      marginBottom: theme.spacing.unit * 2,
    }
  },
});

export default withStyles(styles)(NotFound);
