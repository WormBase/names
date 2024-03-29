import React from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core';

import { useTitle } from '../../../hooks/useTitle';

const Page = (props) => {
  useTitle(props.title);
  return <div className={props.classes.root} {...props} />;
};
Page.propTypes = {
  classes: PropTypes.object.isRequired,
  title: PropTypes.string,
};

function pageMain({ classes, ...others }) {
  return <div className={classes.main} {...others} />;
}
pageMain.propTypes = {
  classes: PropTypes.object.isRequired,
};

function pageLeft({ classes, ...others }) {
  return <div className={classes.left} {...others} />;
}
pageLeft.propTypes = {
  classes: PropTypes.object.isRequired,
};

function pageRight({ classes, ...others }) {
  return <div className={classes.right} {...others} />;
}
pageRight.propTypes = {
  classes: PropTypes.object.isRequired,
};

export default withStyles((theme) => ({
  root: {
    display: 'flex',
    margin: theme.spacing(4),
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column',
    },
  },
}))(Page);

export const PageLeft = withStyles((theme) => ({
  left: {
    width: '20%',
    flex: '0 0 auto',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
      marginBottom: theme.spacing(4),
    },
  },
}))(pageLeft);

export const PageMain = withStyles((theme) => ({
  main: {
    width: '80%',
    flex: '1 0 auto',
    [theme.breakpoints.down('sm')]: {
      margin: 0,
      width: `100%`,
    },
  },
}))(pageMain);

// export const PageRight = withStyles((theme) => ({
//   right: {
//     width: '20%',
//     [theme.breakpoints.down('sm')]: {
//       width: `100%`,
//     },
//   },
// }))(pageRight);
