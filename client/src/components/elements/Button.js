import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { withStyles, Button as MuiButton } from '@material-ui/core';

function Button(props) {
  const { wbVariant, classes, className: classNameProp, ...others } = props;
  const className = classNames(
    { [classes.danger]: wbVariant === 'danger' },
    classNameProp
  );
  return <MuiButton className={className} {...others} />;
}

Button.propTypes = {
  wbVariant: PropTypes.oneOf(['danger']),
};

const styles = (theme) => ({
  danger: {
    backgroundColor: theme.palette.error.main,
    color: theme.palette.error.contrastText,
    '&:hover': {
      backgroundColor: theme.palette.error.dark,
    },
  },
});

export default withStyles(styles)(Button);
