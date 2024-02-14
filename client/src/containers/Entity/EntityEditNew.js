import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { withStyles } from '@material-ui/core/styles';
import { mockFetchOrNot } from '../../mock';
import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';
import {
  BaseForm,
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
} from '../../components/elements';

class EntityEditNew extends Component {
  constructor(props) {
    super(props);
    this.state = {
      error: null,
      status: null,
    };
  }

  // handleClear = () => {
  //   this.setState(
  //     {
  //       error: null,
  //     },
  //     () => {
  //       this.props.history.push('/gene');
  //     }
  //   );
  // };

  getCreateHandler = (getFormData, authorizedFetch) => {
    return () => {
      const { data, prov: provenance } = getFormData() || {};
      const { entityType, apiPrefix } = this.props;
      if (this.state.status === 'SUBMITTED') {
        return;
      }

      this.setState(
        {
          status: 'SUBMITTED',
        },
        () => {
          mockFetchOrNot(
            (mockFetch) => {
              const filled = ['cgc-name', 'species'].reduce(
                (result, fieldId) => {
                  return result && data[fieldId];
                },
                true
              );
              if (filled) {
                return mockFetch.post('*', {
                  created: {
                    ...data,
                    id: 'WBGene00100001',
                    status: 'live',
                  },
                });
              } else {
                return mockFetch.post('*', {
                  error: 'Form is not completed.',
                });
              }
            },
            () => {
              return authorizedFetch(apiPrefix, {
                method: 'POST',
                body: JSON.stringify({
                  data: data,
                  prov: provenance,
                }),
              });
            }
          )
            .then((response) => {
              this.setState({
                error: null,
              });

              if (!response.ok) {
                console.log(
                  'Error response received on entity creation request.'
                );

                let error_message = `API error. Status ${response.status} (${
                  response.statusText
                }) received, but no message.`;

                this.setState({
                  error: { message: error_message },
                });
              }

              return response.json();
            })
            .then((response_body) => {
              if (this.state.error || !response_body.created) {
                console.log(
                  'state.error found or response_body.created not found.'
                );
                console.log('state.error: ', this.state.error);
                console.log('response_body.created: ', response_body.created);

                let new_state = this.state;
                new_state['status'] = 'COMPLETE';

                if (response_body.message) {
                  new_state['error'] = { message: response_body.message };
                } else {
                  console.log('Error response received but no message.');
                  console.log('Full response body:', response_body);
                }

                this.setState(new_state);
              } else {
                let redirect_delay = 0;

                let new_state = this.state;

                new_state['data'] = response_body.created;
                new_state['status'] = 'COMPLETE';

                if ('caltech-sync' in response_body) {
                  let caltech_result = response_body['caltech-sync'];
                  let message = `Entity ${
                    response_body.created.id
                  } created successfully.`;
                  if (
                    [200, 201, 202].includes(
                      caltech_result['http-response-status-code']
                    )
                  ) {
                    redirect_delay += 2000;
                    message += ' Caltech sync successful.';
                  } else {
                    redirect_delay += 3000;
                    message += ` Error returned by Caltech sync API (HTTP Code ${
                      caltech_result['http-response-status-code']
                    }).`;
                  }

                  if ('caltech-message' in caltech_result) {
                    redirect_delay += 2000;
                    message += ` Message returned: "${
                      caltech_result['caltech-message']
                    }".`;
                  }

                  message += ' Redirecting to entity page...';

                  console.log('Caltech sync display message:', message);

                  new_state['error'] = { message: message };
                } else {
                  console.log(
                    'Entity successfully created, but no caltech-sync property found.'
                  );
                  new_state['error'] = null;
                }

                this.setState(new_state, () => {
                  setTimeout(() => {
                    this.props.history.push(
                      `/${entityType}/id/${response_body.created.id}`
                    );
                  }, redirect_delay);
                });
              }
            })
            .catch((e) => {
              console.log('Caught error:', e);
              this.setState({
                error: {
                  message:
                    'Uncaught error: ' +
                    e.toString() +
                    '. See console for more details.',
                },
                status: 'COMPLETE',
              });
            });
        }
      );
    };
  };

  render() {
    const { entityType, data, disabled = false } = this.props;

    const { status, error } = this.state;
    return (
      <AuthorizationContext.Consumer>
        {({ authorizedFetch }) => (
          <BaseForm data={data}>
            {({
              withFieldData,
              getFormData,
              getFormDataModified,
              dirtinessContext,
              resetData,
            }) => {
              return this.props.children({
                profileContext: {
                  entityType: entityType,
                  errorMessage: error,
                  buttonResetProps: {
                    onClick: resetData,
                    disabled: disabled,
                  },
                  buttonSubmitProps: {
                    status:
                      status === 'SUBMITTED'
                        ? PROGRESS_BUTTON_PENDING
                        : PROGRESS_BUTTON_READY,
                    onClick: this.getCreateHandler(
                      getFormData,
                      authorizedFetch
                    ),
                    disabled: status === 'SUBMITTED' || disabled,
                  },
                  withFieldData,
                  dirtinessContext,
                },
                formContext: {
                  entityType: entityType,
                  withFieldData,
                },
                // from BaseForm
                withFieldData,
                dirtinessContext,
                getFormData,
                getFormDataModified,
              });
            }}
          </BaseForm>
        )}
      </AuthorizationContext.Consumer>
    );
  }
}

EntityEditNew.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  apiPrefix: PropTypes.string.isRequired,
  data: PropTypes.any,
  disabled: PropTypes.bool,
};

const styles = (theme) => ({});

export default withStyles(styles)(withRouter(EntityEditNew));
