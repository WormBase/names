import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles }  from 'material-ui';

function page({classes, ...others}) {
  return (
    <div className={classes.root} {...others} />
  );
}
page.propTypes = {
  classes: PropTypes.object.isRequired,
};


function pageMain({classes, ...others}) {
  return (
    <div className={classes.main} {...others} />
  );
}
pageMain.propTypes = {
  classes: PropTypes.object.isRequired,
};


function pageLeft({classes, ...others}) {
  return (
    <div className={classes.left} {...others} />
  );
}
pageLeft.propTypes = {
  classes: PropTypes.object.isRequired,
};


function pageRight({classes, ...others}) {
  return (
    <div className={classes.right} {...others} />
  );
}
pageRight.propTypes = {
  classes: PropTypes.object.isRequired,
};



export const Page = withStyles((theme) => ({
  root: {
    display: 'flex',
    margin: theme.spacing.unit * 4,
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column',
    },
  },
}))(page)

export const PageLeft = withStyles((theme) => ({
  left: {
    width: '20%',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
      marginBottom: theme.spacing.unit * 4,
    },
  },
}))(pageLeft);

export const PageMain = withStyles((theme) => ({
  main: {
    width: '60%',
    [theme.breakpoints.down('sm')]: {
      margin: 0,
      width: `100%`,
    },
  },
}))(pageMain);

export const PageRight = withStyles((theme) => ({
  right: {
    width: '20%',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
    },
  },
}))(pageRight);

export default Page;
