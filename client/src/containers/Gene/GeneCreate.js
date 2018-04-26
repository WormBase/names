import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Page, PageLeft, PageMain, PageRight, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';

class GeneCreate extends Component {

  handleCreateGene = (data) => {
    mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.put('*', {
          data: {
            id: 'WB10',
          },
        });
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
        this.props.history.push(`/gene/id/${response.data.id}`);
      }
    }).catch((e) => console.log('error', e));
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
          <GeneForm
            onSubmit={this.handleCreateGene}
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
