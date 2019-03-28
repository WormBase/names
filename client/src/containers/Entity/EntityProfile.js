import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  CircularProgress,
  Divider,
  Typography,
} from '@material-ui/core';

import EntityForm from './EntityForm';

import {
  BaseForm,
  Button,
  DocumentTitle,
  ErrorBoundary,
  Humanize,
  Page,
  PageLeft,
  PageMain,
  ProgressButton,
  SimpleAjax,
  Snackbar,
  SnackbarContent,
  TextField,
  ValidationError,
} from '../../components/elements';

import EntityDirectoryButton from './EntityDirectoryButton';
import EntityEdit from './EntityEdit';

class EntityProfile extends Component {
  renderStatus({ data, entityType }) {
    return data[`${entityType}/status`] !== `${entityType}.status/live` ? (
      <Typography variant="display1" gutterBottom>
        <Humanize capitalized>{data[`${entityType}/status`]}</Humanize>
      </Typography>
    ) : null;
  }

  renderForm = (formProps) => <EntityForm {...formProps} />;

  render() {
    const {
      classes = {},
      wbId,
      entityType,
      renderDisplayName,
      renderForm = this.renderForm,
      renderChanges,
      renderOperations,
      renderOperationTip,
      renderStatus = this.renderStatus,
    } = this.props;

    return (
      <EntityEdit
        wbId={wbId}
        entityType={entityType}
        renderDisplayName={renderDisplayName}
      >
        {({
          profileContext,
          formContext,
          getDialogProps,
          getOperationProps,
        }) => {
          const {
            withFieldData,
            dirtinessContext,
            dataCommitted: data = {},
            changes = [],
            buttonResetProps,
            buttonSubmitProps,
            errorMessage = null,
            message = null,
            messageVariant = 'info',
            onMessageClose,
          } = profileContext;

          const renderProps = {
            entityType,
            data,
            changes,
          };

          const ReasonField = withFieldData(TextField, 'provenance/why');

          return (
            <DocumentTitle title={`${wbId} (${entityType})`}>
              <Page>
                <PageLeft>
                  <div className={classes.operations}>
                    <EntityDirectoryButton entityType={entityType} />
                    <Divider light />
                    {renderOperations &&
                      renderOperations({
                        ...renderProps,
                        getOperationProps,
                        getDialogProps,
                      })}
                    {renderOperationTip
                      ? renderOperationTip &&
                        renderOperationTip({
                          ...renderProps,
                          Wrapper: ({ children }) => (
                            <React.Fragment>
                              <h5>Tip:</h5>
                              {children}
                            </React.Fragment>
                          ),
                        })
                      : null}
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
                      {renderStatus(renderProps)}
                      {renderForm ? (
                        <ErrorBoundary>
                          {renderForm({ ...renderProps, ...formContext })}
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
                        <ErrorBoundary>
                          {renderChanges(renderProps)}
                        </ErrorBoundary>
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
        }}
      </EntityEdit>
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
