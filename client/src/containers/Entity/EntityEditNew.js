import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { withStyles } from '@material-ui/core/styles';
import { mockFetchOrNot } from '../../mock';
import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';
import {
  BaseForm,
  Button,
  ProgressButton,
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
  SimpleAjax,
  TextField,
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
      const { entityType } = this.props;
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
              const filled = ['gene/cgc-name', 'gene/species'].reduce(
                (result, fieldId) => {
                  return result && data[fieldId];
                },
                true
              );
              if (filled) {
                return mockFetch.post('*', {
                  created: {
                    ...data,
                    'gene/id': 'WBGene00100001',
                    'gene/status': 'gene.status/live',
                  },
                });
              } else {
                return mockFetch.post('*', {
                  error: 'Form is not completed.',
                });
              }
            },
            () => {
              return authorizedFetch(`/api/${entityType}/`, {
                method: 'POST',
                body: JSON.stringify({
                  data: data,
                  prov: provenance,
                }),
              });
            }
          )
            .then((response) => {
              return response.json();
            })
            .then((response) => {
              if (!response.created) {
                this.setState({
                  error: response,
                  status: 'COMPLETE',
                });
              } else {
                this.setState(
                  {
                    data: response.created,
                    error: null,
                    status: 'COMPLETE',
                  },
                  () => {
                    this.props.history.push(
                      `/${entityType}/id/${
                        response.created[`${entityType}/id`]
                      }`
                    );
                  }
                );
              }
            })
            .catch((e) => console.log('error', e));
        }
      );
    };
  };

  render() {
    const {
      classes,
      entityType,
      data,
      submitter,
      create = false,
      disabled = false,
      ...others
    } = this.props;

    const { status, error } = this.state;
    return (
      <AuthorizationContext.Consumer>
        {({ authorizedFetch }) => (
          <BaseForm data={data}>
            {({ withFieldData, getFormData, dirtinessContext, resetData }) => {
              return this.props.children({
                getProfileProps: () => ({
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
                }),
                getFormProps: () => ({
                  withFieldData,
                }),
                // from BaseForm
                withFieldData,
                dirtinessContext,
                getFormData,
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
  authorizedFetch: PropTypes.func.isRequired,
  data: PropTypes.any,
  submitter: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
  create: PropTypes.bool,
};

const styles = (theme) => ({});

export default withStyles(styles)(withRouter(EntityEditNew));
