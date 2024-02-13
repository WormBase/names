import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@material-ui/core';

export default function EntityDirectoryButton(props) {
  const { entityType } = props;

  const entity_directory_link = React.forwardRef(function(props, ref) {
    return <Link to={`/${entityType}`} {...props} ref={ref} />;
  });

  return (
    <Button variant="contained" component={entity_directory_link}>
      Back to directory
    </Button>
  );
}
