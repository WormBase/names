import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Page, PageLeft, PageMain, PageRight, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
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
            onSubmit={(data) => {console.log('create gene', data);}}
          />
        </PageMain>
      </Page>
    );
  }
}

export default GeneCreate;
