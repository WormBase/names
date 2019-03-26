import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { EntityEditNew, Button, EntityCreate } from '../../components/elements';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
  render() {
    const { authorizedFetch } = this.props;
    return (
      <EntityEditNew entityType={'gene'} authorizedFetch={authorizedFetch}>
        {({ getFormProps, getProfileProps }) => {
          return (
            <EntityCreate
              {...getProfileProps()}
              entityType="gene"
              renderForm={() => (
                <React.Fragment>
                  <GeneForm {...getFormProps()} />
                </React.Fragment>
              )}
            />
          );
        }}
      </EntityEditNew>
    );
  }
}

GeneCreate.propTypes = {
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

export default GeneCreate;
