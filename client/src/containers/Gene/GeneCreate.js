import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Page, PageLeft, PageMain, PageRight, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';
import { authorizedFetch } from '../Authenticate';

class GeneCreate extends Component {
  constructor(props) {
    super(props);
    this.state = {
      error: null,
    };
  }

  handleCreateGene = (data) => {
    mockFetchOrNot(
      (mockFetch) => {
        const filled = ['gene/cgc-name', 'gene/species'].reduce((result, fieldId) => {
          return result && data[fieldId];
        }, true);
        if (filled) {
          return mockFetch.post('*', {
            "created": {
              ...data,
              "gene/species":{
                ...data['gene/species'],
                "species/cgc-name-pattern":"^[a-z21]{3,4}-[1-9]{1}\\d*",
                "species/sequence-name-pattern":"^[A-Z0-9_cel]+\\.[1-9]\\d{0,3}[A-Za-z]?$"
              },
              "gene/id":"WBGene00100001",
              "gene/status":"gene.status/live"
            },
          });
        } else {
          return mockFetch.post('*', {
            error: 'Form is not completed.'
          });
        }
      },
      () => {
        return authorizedFetch(`/api/gene/`, {
          method: 'POST',
          body: JSON.stringify({
            ...data
          }),
        });
      },
      true
    ).then((response) => {
      return response.json();
    }).then((response) => {
      if (!response.created) {
        this.setState({
          error: response.message,
        });
      } else {
        this.setState({
          data: response.created,
          error: null,
        }, () => {
          this.props.history.push(`/gene/id/${response.created['gene/id']}`);
        });
      }
    }).catch((e) => console.log('error', e));
  }

  handleClear = () => {
    this.setState({
      error: null,
    }, () => {
      this.props.history.push('/gene');
    });
  }

  render() {
    return (
      <Page>
        <PageLeft>
          <Button
            variant="raised"
            component={({...props}) => <Link to='/gene' {...props} />}
          >
            Back to directory
          </Button>
        </PageLeft>
        <PageMain>
          <Typography variant="headline" gutterBottom>Add gene</Typography>
          <Typography color="error">{this.state.error}</Typography>
          <GeneForm
            onSubmit={this.handleCreateGene}
            onCancel={this.handleClear}
          />
        </PageMain>
      </Page>
    );
  }
}

GeneCreate.propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

export default withRouter(GeneCreate);
