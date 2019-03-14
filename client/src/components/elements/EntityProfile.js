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
    } = this.props;

    return (
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
                <ErrorBoundary>{renderForm()}</ErrorBoundary>
              ) : null}
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
    );
  }
}

EntityProfile.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  wbId: PropTypes.string.isRequired,
  errorMessage: PropTypes.string,
  message: PropTypes.string,
  messageVariant: PropTypes.oneOf(['info', 'warning']),
  onMessageClose: PropTypes.func,
  renderForm: PropTypes.func,
  renderChanges: PropTypes.func,
  renderOperationTip: PropTypes.func,
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
});

export default withStyles(styles)(EntityProfile);
