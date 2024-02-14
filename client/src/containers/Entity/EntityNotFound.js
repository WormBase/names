import React from 'react';
import { Link } from 'react-router-dom';
import { withStyles, Button, Typography } from '@material-ui/core';

import { NotFound } from '../../components/elements';
import EntityDirectoryButton from './EntityDirectoryButton';

function EntityNotFound(props) {
  const { classes = {}, wbId, entityType } = props;

  const new_entity_link = React.forwardRef(function(props, ref) {
    return <Link to={`/${entityType}/new`} {...props} ref={ref} />;
  });

  return (
    <NotFound>
      <Typography>
        <strong>{wbId}</strong> does not exist
      </Typography>
      <div className={classes.operations}>
        <EntityDirectoryButton entityType={entityType} />
        <Button
          variant="contained"
          color="secondary"
          component={new_entity_link}
        >
          Create {entityType}
        </Button>
      </div>
    </NotFound>
  );
}

const styles = (theme) => ({
  operations: {
    display: 'flex',
    flexDirection: 'column',
    width: 200,
    '& > *': {
      marginBottom: theme.spacing(1),
    },
    [theme.breakpoints.down('sm')]: {
      width: '100%',
      alignItems: 'stretch',
    },
  },
});

export default withStyles(styles)(EntityNotFound);
