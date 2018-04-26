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
    flexWrap: 'wrap',
  },
}))(page)

export const PageLeft = withStyles((theme) => ({
  left: {
    minWidth: '20%',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
    },
  },
}))(pageLeft);

export const PageMain = withStyles((theme) => ({
  main: {
    flexGrow: 1,
    margin: `0px ${theme.spacing.unit * 10}px`,
    [theme.breakpoints.down('sm')]: {
      margin: 0,
    },
  },
}))(pageMain);

export const PageRight = withStyles((theme) => ({
  right: {
    minWidth: '20%',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
    },
  },
}))(pageRight);

export default Page;
