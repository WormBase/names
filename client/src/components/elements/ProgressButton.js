import React from 'react';
import PropTypes from 'prop-types';
import { Button, CircularProgress, withStyles } from '@material-ui/core';

export const PROGRESS_BUTTON_READY = 'READY';
export const PROGRESS_BUTTON_PENDING = 'PENDING';

const ProgressButton =  ({classes, status, children, ...buttonProps}) => (
  <div className={classes.wrapper}>
    <Button
      disabled={status === PROGRESS_BUTTON_PENDING}
      {...buttonProps}
    >{children}</Button>
    {
      status === PROGRESS_BUTTON_PENDING ?
        <CircularProgress size={24} className={classes.spinner} /> :
        null
    }
  </div>
);

ProgressButton.propTypes = {
  children: PropTypes.element,
  status: PropTypes.oneOf([PROGRESS_BUTTON_READY, PROGRESS_BUTTON_PENDING]),
};

const styles = (theme) => ({
  wrapper: {
    position: 'relative',
    display: 'inline-block',
  },
  spinner: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginTop: -12,
    marginLeft: -12,
  },
});

export default withStyles(styles)(ProgressButton);
