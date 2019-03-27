import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { Prompt } from 'react-router';
import { withStyles } from '@material-ui/core/styles';
import CircularProgress from '@material-ui/core/CircularProgress';
import Button from './Button';
import TextField from './TextField';
import ProgressButton, {
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
} from './ProgressButton';
import EntityNotFound from './EntityNotFound';
import BaseForm from './BaseForm';
import SimpleAjax from './SimpleAjax';
import { mockFetchOrNot } from '../../mock';
import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';

class EntityEditForm extends Component {
  constructor(props) {
    super(props);
    this.state = {
      status: 'BEGIN',
      errorMessage: null,
      shortMessage: null,
      shortMessageVariant: 'info',
      data: {},
      changes: [],
      dialog: null,
    };
  }

  componentDidMount() {
    this.fetchData();
  }

  fetchData = () => {
    this.setState(
      (prevState) => ({
        status: prevState.status === 'BEGIN' ? 'LOADING' : 'SUBMITTED',
      }),
      () => {
        mockFetchOrNot(
          (mockFetch) => {
            const historyMock = [
              {
                'provenance/how': 'agent/web',
                'provenance/what': 'event/merge-genes',
                'provenance/who': {
                  'person/id': 'WBPerson12346',
                },
                'provenance/when': '2018-08-09T22:09:16Z',
                'provenance/merged-from': {
                  'gene/id': 'WBGene00303223',
                },
                'provenance/merged-into': {
                  'gene/id': this.getId(),
                },
              },
              {
                'provenance/how': 'agent/web',
                'provenance/what': 'event/update-gene',
                'provenance/who': {
                  'person/id': 'WBPerson12346',
                },
                'provenance/when': '2018-08-08T17:27:31Z',
              },
              {
                'provenance/how': 'agent/web',
                'provenance/what': 'event/update-gene',
                'provenance/who': {
                  'person/id': 'WBPerson12346',
                },
                'provenance/when': '2018-08-08T17:27:22Z',
              },
              {
                'provenance/how': 'agent/web',
                'provenance/what': 'event/split-gene',
                'provenance/who': {
                  'person/id': 'WBPerson12346',
                },
                'provenance/when': '2018-08-08T16:50:46Z',
                'provenance/split-from': {
                  'gene/id': this.getId(),
                },
                'provenance/split-into': {
                  'gene/id': 'WBGene00303222',
                },
              },
              {
                'provenance/how': 'agent/web',
                'provenance/what': 'event/split-gene',
                'provenance/who': {
                  'person/id': 'WBPerson12346',
                },
                'provenance/when': '2018-08-08T15:21:07Z',
                'provenance/split-from': {
                  'gene/id': this.getId(),
                },
                'provenance/split-into': {
                  'gene/id': 'WBGene00303219',
                },
              },
              {
                'provenance/how': 'agent/web',
                'provenance/what': 'event/new-gene',
                'provenance/who': {
                  'person/id': 'WBPerson12346',
                },
                'provenance/when': '2018-07-23T15:25:17Z',
              },
            ];

            return mockFetch.get('*', {
              'gene/species': 'Caenorhabditis elegans',
              'gene/cgc-name': 'abi-1',
              'gene/status': 'gene.status/live',
              'gene/biotype': 'biotype/cds',
              'gene/id': this.getId(),
              history: historyMock,
            });
          },
          () => {
            return fetch(`/api/gene/${this.getId()}`, {});
          }
        )
          .then((response) => {
            const nextStatus =
              response.status === 404 ? 'NOT_FOUND' : 'COMPLETE';
            return Promise.all([nextStatus, response.json()]);
          })
          .then(([nextStatus, { history: changes, ...data }]) => {
            this.setState(
              {
                data: data,
                changes: changes,
                status: nextStatus,
              },
              () => {
                const permanentUrl = `/gene/id/${this.getId()}`;
                if (
                  nextStatus === 'COMPLETE' &&
                  this.props.history.location.pathname !== permanentUrl
                ) {
                  this.props.history.replace(`/gene/id/${this.getId()}`);
                }
              }
            );
          })
          .catch((e) => console.log('error', e));
      }
    );
  };

  getUpdateHandler = (getFormDataModified, authorizedFetch) => {
    return () => {
      const { data = {}, prov: provenance } = getFormDataModified() || {};
      if (Object.keys(data).length === 0) {
        this.setState({
          status: 'COMPLETE',
          shortMessage:
            "You didn't modify anything in the form. No change is submitted",
          shortMessageVariant: 'warning',
        });
        return;
      }
      return mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.put('*', {
            updated: {
              ...data,
            },
          });
        },
        () => {
          const dataSubmit = Object.keys(data).reduce((result, key) => {
            if (
              key !== 'split-from' &&
              key !== 'split-into' &&
              key !== 'merged-from' &&
              key !== 'merged-into'
            ) {
              result[key] = data[key];
            }
            return result;
          }, {});
          return authorizedFetch(`/api/gene/${this.getId()}`, {
            method: 'PUT',
            body: JSON.stringify({
              data: dataSubmit,
              prov: provenance,
            }),
          })
            .then((response) => Promise.all([response.ok, response.json()]))
            .then(([ok, response]) => {
              this.setState(() => {
                const stateChanges = {
                  status: 'COMPLETE',
                };
                if (!ok || response.problems) {
                  return {
                    ...stateChanges,
                    errorMessage: response,
                  };
                } else {
                  this.fetchData();
                  return {
                    ...stateChanges,
                    errorMessage: null,
                    data: response.updated,
                    shortMessage: 'Update successful!',
                    shortMessageVariant: 'success',
                  };
                }
              });
            })
            .catch((e) => console.log('error', e));
        }
      );
    };
  };

  closeDialog = () => {
    this.setState({
      dialog: null,
    });
  };

  handleMessageClose = () => {
    this.setState({
      shortMessage: null,
      shortMessageVariant: 'info',
    });
  };

  getId = (data = {}) => {
    const { entityType } = this.props;
    return data[`${entityType}/id`] || this.props.wbId;
  };

  render() {
    const {
      classes,
      wbId: id,
      entityType,
      renderDisplayName,
      ...others
    } = this.props;

    const { data = {}, changes = [], status } = this.state;

    const disabled =
      data[`${entityType}/status`] === `${entityType}.status/dead`;
    const wbId = data[`${entityType}/id`];

    return this.state.status === 'NOT_FOUND' ? (
      <EntityNotFound entityType="gene" wbId={wbId} />
    ) : this.state.status === 'LOADING' ? (
      <CircularProgress />
    ) : (
      <AuthorizationContext.Consumer>
        {({ authorizedFetch }) => (
          <BaseForm data={this.state.data} disabled={disabled}>
            {({
              withFieldData,
              getFormData,
              getFormDataModified,
              getFormProps,
              dirtinessContext,
              resetData,
            }) => {
              return (
                <div>
                  {this.props.children({
                    dataCommitted: this.state.data,
                    changes: changes,
                    getProfileProps: () => ({
                      entityType: entityType,
                      wbId: wbId,
                      dataCommitted: this.state.data,
                      changes: changes,
                      errorMessage: this.state.errorMessage,
                      message: this.state.shortMessage,
                      messageVariant: this.state.shortMessageVariant,
                      onMessageClose: this.handleMessageClose,
                      buttonResetProps: {
                        onClick: resetData,
                        disabled: disabled,
                      },
                      buttonSubmitProps: {
                        status:
                          status === 'SUBMITTED'
                            ? PROGRESS_BUTTON_PENDING
                            : PROGRESS_BUTTON_READY,
                        onClick: this.getUpdateHandler(
                          getFormDataModified,
                          authorizedFetch
                        ),
                        disabled: status === 'SUBMITTED' || disabled,
                      },
                      withFieldData,
                      dirtinessContext,
                    }),
                    getFormProps: () => ({
                      withFieldData,
                    }),
                    getOperationProps: (operation) => ({
                      onClick: () => {
                        this.setState({
                          dialog: operation,
                        });
                      },
                    }),
                    getDialogProps: (operation) => ({
                      wbId: wbId,
                      name: renderDisplayName(data),
                      data: data,
                      open: this.state.dialog === operation,
                      onClose: this.closeDialog,
                      onSubmitSuccess: (data) => {
                        this.setState(
                          {
                            shortMessage: `${operation} successful!`,
                            shortMessageVariant: 'success',
                          },
                          () => {
                            this.fetchData();
                            this.closeDialog();
                          }
                        );
                      },
                    }),
                    // from BaseForm
                    withFieldData,
                    getFormData,
                    dirtinessContext,
                  })}
                  {dirtinessContext(({ dirty }) => (
                    <Prompt
                      when={dirty}
                      message="Form contains unsubmitted content, which will be lost when you leave. Are you sure you want to leave?"
                    />
                  ))}
                </div>
              );
            }}
          </BaseForm>
        )}
      </AuthorizationContext.Consumer>
    );
  }
}

EntityEditForm.propTypes = {
  classes: PropTypes.object.isRequired,
  wbId: PropTypes.string.isRequired,
  entityType: PropTypes.string.isRequired,
  data: PropTypes.any,
  renderDisplayName: PropTypes.func,
  history: PropTypes.shape({
    replace: PropTypes.func.isRequired,
  }).isRequired,
};

const styles = (theme) => ({});

export default withStyles(styles)(withRouter(EntityEditForm));
