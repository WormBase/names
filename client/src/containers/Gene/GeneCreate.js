import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { EntityEditNew, Button, EntityCreate } from '../../components/elements';
import GeneForm from './GeneForm';

class GeneCreate extends Component {
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
    const { authorizedFetch } = this.props;
    return (
      <EntityEditNew entityType={'gene'} authorizedFetch={authorizedFetch}>
        {({ withFieldData, dirtinessContext, getProfileProps }) => {
          return (
            <EntityCreate
              {...getProfileProps()}
              entityType="gene"
              renderForm={() => (
                <React.Fragment>
                  <GeneForm
                    withFieldData={withFieldData}
                    dirtinessContext={dirtinessContext}
                  />
                </React.Fragment>
              )}
              renderOperations={this.renderOperations}
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
