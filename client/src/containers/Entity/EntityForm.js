import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { TextField } from '../../components/elements';

class EntityForm extends Component {
  render() {
    const { withFieldData, entityType } = this.props;
    const NameField = withFieldData(TextField, 'name');
    return <NameField label="Name" helperText={`Name of the ${entityType}`} />;
  }
}

EntityForm.propTypes = {
  withFieldData: PropTypes.func.isRequired,
  entityType: PropTypes.string.isRequired,
};

export default EntityForm;
