import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  CircularProgress,
  Divider,
  Typography,
} from '@material-ui/core';

import {
  Button,
  DocumentTitle,
  ErrorBoundary,
  Humanize,
  Page,
  PageLeft,
  PageMain,
  ProgressButton,
  Snackbar,
  SnackbarContent,
  TextArea,
  ValidationError,
} from '../../components/elements';

import EntityForm from './EntityForm';
import EntityDirectoryButton from './EntityDirectoryButton';
import EntityEdit from './EntityEdit';
import EntityDialogKill from './EntityDialogKill';
import EntityDialogResurrect from './EntityDialogResurrect';
import EntityHistory from './EntityHistory';

const OPERATION_KILL = 'kill';
const OPERATION_RESURRECT = 'resurrect';

class EntityProfile extends Component {
  renderStatus({ data, entityType }) {
    return data['status'] !== 'live' ? (
      <Typography variant="display1" gutterBottom>
        <Humanize capitalized>{data['status']}</Humanize>
      </Typography>
    ) : null;
  }

  renderDisplayName = (data = {}) => {
    return data['name'] || data['id'];
  };

  renderForm = (formProps) => <EntityForm {...formProps} />;

  renderOperations = ({ data, changes, getOperationProps, getDialogProps }) => {
    const { entityType } = this.props;
    const live = data['status'] === 'live';
    return (
      <React.Fragment>
        {live ? (
          <Button
            {...getOperationProps(OPERATION_KILL)}
            wbVariant="danger"
            variant="contained"
          >
            Kill {entityType}
          </Button>
        ) : (
          <Button
            {...getOperationProps(OPERATION_RESURRECT)}
            wbVariant="danger"
            variant="contained"
          >
            Resurrect {entityType}
          </Button>
        )}
        <EntityDialogKill {...getDialogProps(OPERATION_KILL)} />
        <EntityDialogResurrect {...getDialogProps(OPERATION_RESURRECT)} />
      </React.Fragment>
    );
  };

  renderChanges = ({ data = {}, changes = [] }) => {
    const { wbId, entityType } = this.props;
    return (
      <EntityHistory wbId={wbId} activities={changes} entityType={entityType} />
    );
  };

  render() {
    const {
      classes = {},
      wbId,
      entityType,
      apiPrefix = `/api/entity/${entityType}`,
      renderDisplayName = this.renderDisplayName,
      renderForm = this.renderForm,
      renderChanges = this.renderChanges,
      renderOperations = this.renderOperations,
      renderOperationTip,
      renderStatus = this.renderStatus,
    } = this.props;

    return (
      <EntityEdit
        wbId={wbId}
        entityType={entityType}
        apiPrefix={apiPrefix}
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
            buttonCopyProps,
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

          const ReasonField = withFieldData(TextArea, 'why');

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
                    {entityType} <em>{wbId}</em>{' '}
                    <Button
                      variant="contained"
                      color="primary"
                      {...buttonCopyProps}
                    />
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
                                multiline
                                label="Reason"
                                helperText={`Why do you update this ${entityType}?`}
                              />
                            ) : null
                          )}
                        </ErrorBoundary>
                      ) : null}
                      <div className={classes.actions}>
                        <Button variant="contained" {...buttonResetProps}>
                          Reset
                        </Button>
                        <ProgressButton
                          variant="contained"
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
                  transitionDuration={0}
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
  apiPrefix: PropTypes.string,
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
