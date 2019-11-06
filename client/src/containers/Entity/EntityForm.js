import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { TextField } from '../../components/elements';
import { useEntityTypes } from './EntityTypesContextProvider';

function EntityForm({ withFieldData, entityType }) {
  const { getEntityType } = useEntityTypes();
  const entity = getEntityType(entityType) || {};
  const NameField = withFieldData(TextField, 'name');
  return (
    <NameField
      label={`Name ${entity['named?'] ? '' : '(Optional)'}`}
      helperText={`Name of the ${entityType}`}
    />
  );
}

EntityForm.propTypes = {
  withFieldData: PropTypes.func.isRequired,
  entityType: PropTypes.string.isRequired,
};

export default EntityForm;
