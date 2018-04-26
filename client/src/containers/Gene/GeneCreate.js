import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Page, PageLeft, PageMain, PageRight, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';

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
        const filled = ['cgcName', 'species', 'biotype'].reduce((result, fieldId) => {
          return result && data[fieldId];
        }, true);
        if (filled) {
          return mockFetch.put('*', {
            data: {
              ...data,
              id: 'WB10',
            },
          });
        } else {
          return mockFetch.put('*', {
            error: 'Form is not completed.'
          });
        }
      },
      () => {
        return fetch(`/api/gene`, {
          method: 'PUT'
        });
      },
      true
    ).then((response) => response.json()).then((response) => {
      if (response.error) {
        this.setState({
          error: response.error,
        });
      } else {
        this.setState({
          data: response.data,
          error: null,
        }, () => {
          this.props.history.push(`/gene/id/${response.data.id}`);
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
