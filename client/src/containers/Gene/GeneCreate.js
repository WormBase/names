import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { EntityEditNew, EntityCreate } from '../Entity';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
  render() {
    return (
      <EntityEditNew entityType={'gene'}>
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

GeneCreate.propTypes = {};

export default GeneCreate;
