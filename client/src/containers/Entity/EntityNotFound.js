import React from 'react';
import { Link } from 'react-router-dom';
import { withStyles, Button, Typography } from '@material-ui/core';

import { DocumentTitle, NotFound } from '../../components/elements';
import EntityDirectoryButton from './EntityDirectoryButton';

function EntityNotFound(props) {
  const { classes = {}, wbId, entityType } = props;
  return (
    <DocumentTitle title="Not found">
      <NotFound>
        <Typography>
          <strong>{wbId}</strong> does not exist
        </Typography>
        <div className={classes.operations}>
          <EntityDirectoryButton entityType={entityType} />
          <Button
            variant="raised"
            color="secondary"
            component={({ ...props }) => (
              <Link to={`/${entityType}/new`} {...props} />
            )}
          >
            Create {entityType}
          </Button>
        </div>
      </NotFound>
    </DocumentTitle>
  );
}

const styles = (theme) => ({
  operations: {
    display: 'flex',
    flexDirection: 'column',
    width: 150,
    '& > *': {
      marginBottom: theme.spacing.unit,
    },
    [theme.breakpoints.down('sm')]: {
      width: '100%',
      alignItems: 'stretch',
    },
  },
});

export default withStyles(styles)(EntityNotFound);
