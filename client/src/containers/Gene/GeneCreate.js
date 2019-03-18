import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { AjaxForm, Button, EntityCreate } from '../../components/elements';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      error: null,
      status: null,
    };
  }

  handleCreateGene = ({ data, prov: provenance }) => {
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
            return this.props.authorizedFetch(`/api/gene/`, {
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
                    `/gene/id/${response.created['gene/id']}`
                  );
                }
              );
            }
          })
          .catch((e) => console.log('error', e));
      }
    );
  };

  handleClear = () => {
    this.setState(
      {
        error: null,
      },
      () => {
        this.props.history.push('/gene');
      }
    );
  };

  renderForm = () => {
    return (
      <AjaxForm
        entityType={'gene'}
        submitter={this.handleCreateGene}
        //  onCancel={this.handleClear}
        create={true}
      >
        {({ withFieldData }) => <GeneForm withFieldData={withFieldData} />}
      </AjaxForm>
    );
  };

  renderOperations = () => {
    return (
      <Button
        variant="raised"
        component={({ ...props }) => <Link to="/gene" {...props} />}
        className={this.props.classes.backToDirectoryButton}
      >
        Back to directory
      </Button>
    );
  };

  render() {
    return (
      <EntityCreate
        entityType="gene"
        errorMessage={this.state.error}
        renderForm={this.renderForm}
        renderOperations={this.renderOperations}
      />
    );
  }
}

GeneCreate.propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

export default withRouter(GeneCreate);
