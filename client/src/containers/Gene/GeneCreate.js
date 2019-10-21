import React, { Component } from 'react';

import { EntityCreate } from '../Entity';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
  render() {
    const { ...others } = this.props;
    return (
      <EntityCreate
        {...others}
        apiPrefix="/api/gene"
        renderForm={(props) => <GeneForm {...props} />}
      />
    );
  }
}

GeneCreate.propTypes = {};

export default GeneCreate;
