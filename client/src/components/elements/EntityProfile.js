import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  CircularProgress,
  Divider,
  Typography,
} from '@material-ui/core';

import Button from './Button';
import ProgressButton from './ProgressButton';
import DocumentTitle from './DocumentTitle';
import EntityDirectoryButton from './EntityDirectoryButton';
import ErrorBoundary from './ErrorBoundary';
import { Page, PageLeft, PageMain } from './Page';
import Snackbar from './Snackbar';
import SnackbarContent from './SnackbarContent';
import TextField from './TextField';
import ValidationError from './ValidationError';

class EntityProfile extends Component {
  render() {
    const {
      classes = {},
      wbId,
      entityType,
      errorMessage = null,
      message = null,
      messageVariant = 'info',
      onMessageClose,
      renderForm,
      renderChanges,
      renderOperations,
      renderOperationTip,
      renderStatus,
      buttonResetProps,
      buttonSubmitProps,
      withFieldData,
      dirtinessContext,
    } = this.props;

    const ReasonField = withFieldData(TextField, 'provenance/why');

    return (
      <DocumentTitle title={`${wbId} (${entityType})`}>
        <Page>
          <PageLeft>
            <div className={classes.operations}>
              <EntityDirectoryButton entityType={entityType} />
              <Divider light />
              {renderOperations && renderOperations()}
              {renderOperationTip ? (
                <React.Fragment>
                  <h5>Tip:</h5>
                  {renderOperationTip && renderOperationTip()}
                </React.Fragment>
              ) : null}
            </div>
          </PageLeft>
          <PageMain>
            <Typography variant="headline" gutterBottom>
              {entityType} <em>{wbId}</em>
            </Typography>
            <ValidationError {...errorMessage} />
            {this.props.status === 'LOADING' ? (
              <CircularProgress />
            ) : (
              <div>
                {renderStatus && renderStatus()}
                {renderForm ? (
                  <ErrorBoundary>
                    {renderForm()}
                    {dirtinessContext(({ dirty }) =>
                      dirty ? (
                        <ReasonField
                          label="Reason"
                          helperText={`Why do you create this gene?`}
                        />
                      ) : null
                    )}
                  </ErrorBoundary>
                ) : null}
                <div className={classes.actions}>
                  <Button variant="raised" {...buttonResetProps}>
                    Reset
                  </Button>
                  <ProgressButton
                    variant="raised"
                    color="secondary"
                    {...buttonSubmitProps}
                  >
                    Update
                  </ProgressButton>
                </div>
              </div>
            )}
            <div className={classes.section}>
              <Typography variant="title" gutterBottom>
                Change history
              </Typography>
              {renderChanges ? (
                <div className={classes.historyTable}>
                  <ErrorBoundary>{renderChanges()}</ErrorBoundary>
                </div>
              ) : null}
            </div>
          </PageMain>

          <Snackbar
            anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
            open={message}
            onClose={onMessageClose}
          >
            <SnackbarContent
              variant={messageVariant}
              message={<span>{message}</span>}
              onClose={onMessageClose}
            />
          </Snackbar>
        </Page>
      </DocumentTitle>
    );
  }
}

EntityProfile.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  wbId: PropTypes.string.isRequired,
  withFieldData: PropTypes.func.isRequired,
  dirtinessContext: PropTypes.func.isRequired,
  errorMessage: PropTypes.string,
  message: PropTypes.string,
  messageVariant: PropTypes.oneOf(['info', 'warning']),
  onMessageClose: PropTypes.func,
  renderForm: PropTypes.func,
  renderChanges: PropTypes.func,
  renderOperationTip: PropTypes.func,
  buttonResetProps: PropTypes.func,
  buttonSubmitProps: PropTypes.func,
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
  section: {
    margin: `${theme.spacing.unit * 8}px 0`,
  },
  historyTable: {},
  actions: {
    marginTop: theme.spacing.unit * 2,
    '& > *': {
      marginRight: theme.spacing.unit,
      width: 150,
      [theme.breakpoints.down('xs')]: {
        width: `calc(50% - ${theme.spacing.unit}px)`,
      },
    },
  },
});

export default withStyles(styles)(EntityProfile);
