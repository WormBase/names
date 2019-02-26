import React from 'react';
import { withStyles } from '@material-ui/core';

const styles = (theme) => ({
  root: {
    flexGrow: 1,
  },
  or: {
    [theme.breakpoints.down('xs')]: {
      display: 'none',
    },
  },
  search: {
    marginTop: theme.spacing.unit * 2,
  },
  main: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    '& > *': {
      width: '100%',
    },
  },
  recentActivitiesTable: {
    overflow: 'scroll',
  },
});

export const LayoutIndexPageBanner = withStyles((theme) => ({
  root: {
    display: 'flex',
    justifyContent: 'space-around',
    alignItems: 'center',
    marginBottom: theme.spacing.unit * 6,
    [theme.breakpoints.down('xs')]: {
      flexDirection: 'column',
    },
  },
}))(({ classes, children }) => <div className={classes.root}>{children}</div>);
