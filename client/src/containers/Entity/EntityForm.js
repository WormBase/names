import React from 'react';
import PropTypes from 'prop-types';
import { TextField } from '../../components/elements';
import useEntityTypes from './useEntityTypes';

function EntityForm({ withFieldData, entityType }) {
  const { getEntityType } = useEntityTypes();
  const entity = getEntityType(entityType) || {};
  const NameField = withFieldData(TextField, 'name');
  return entity['named?'] ? (
    <NameField label="Name" helperText={`Name of the ${entityType}`} />
  ) : null;
}

EntityForm.propTypes = {
  withFieldData: PropTypes.func.isRequired,
  entityType: PropTypes.string.isRequired,
};

export default EntityForm;
