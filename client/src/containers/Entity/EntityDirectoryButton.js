import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@material-ui/core';

export default function EntityDirectoryButton(props) {
  const { entityType } = props;

  return (
    <Button
      variant="contained"
      component={({ ...props }) => <Link to={`/${entityType}`} {...props} />}
    >
      Back to directory
    </Button>
  );
}
