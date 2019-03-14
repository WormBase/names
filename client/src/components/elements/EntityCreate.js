import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  CircularProgress,
  Divider,
  Typography,
} from '@material-ui/core';

import EntityDirectoryButton from './EntityDirectoryButton';
import ErrorBoundary from './ErrorBoundary';
import { Page, PageLeft, PageMain } from './Page';
import Snackbar from './Snackbar';
import SnackbarContent from './SnackbarContent';
import ValidationError from './ValidationError';

class EntityCreate extends Component {
  render() {
    const {
      classes = {},
      entityType,
      errorMessage = null,
      renderForm,
    } = this.props;

    return (
      <Page>
        <PageLeft>
          <div className={classes.operations}>
            <EntityDirectoryButton entityType={entityType} />
          </div>
        </PageLeft>
        <PageMain>
          <Typography variant="headline" gutterBottom>
            Add {entityType}
          </Typography>
          <ValidationError {...errorMessage} />
          {renderForm ? <ErrorBoundary>{renderForm()}</ErrorBoundary> : null}
        </PageMain>
      </Page>
    );
  }
}

EntityCreate.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  errorMessage: PropTypes.string,
  renderForm: PropTypes.func,
};

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

export default withStyles(styles)(EntityCreate);
