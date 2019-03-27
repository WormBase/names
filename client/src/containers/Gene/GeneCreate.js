import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { EntityEditNew, EntityCreate } from '../Entity';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
  render() {
    return (
      <EntityCreate
        entityType="gene"
        renderForm={({ props }) => <GeneForm {...props} />}
      />
    );
  }
}

GeneCreate.propTypes = {};

export default GeneCreate;
