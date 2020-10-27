import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  BiotypeSelect,
  TextField,
  SpeciesSelect,
  ListField,
} from '../../components/elements';

class GeneForm extends Component {
  render() {
    const {
      withFieldData,
      cloned = false,
      isEdit = false,
      addNamesOtherButton,
    } = this.props;
    const CgcNameField = withFieldData(TextField, 'cgc-name');
    const SequenceNameField = withFieldData(TextField, 'sequence-name');
    const SpeciesSelectField = withFieldData(SpeciesSelect, 'species');
    const BiotypeSelectField = withFieldData(BiotypeSelect, 'biotype');
    const OtherNamesField = withFieldData(ListField, 'other-names');

    return (
      <React.Fragment>
        <CgcNameField
          label="CGC name"
          helperText="Enter the CGC name of the gene"
          autoFocus
        />
        <br />
        <SpeciesSelectField required />
        <br />
        <SequenceNameField label="Sequence name" />
        <BiotypeSelectField
          required={cloned} // once a cloned gene, always a cloned gene
          helperText={
            "For cloned genes, biotype is required. Otherwise, it's optional"
          }
        />
        <br />
        {isEdit ? (
          addNamesOtherButton
        ) : (
          <OtherNamesField label="Alternative name(s)" />
        )}
      </React.Fragment>
    );
  }
}

GeneForm.propTypes = {
  withFieldData: PropTypes.func.isRequired,
  cloned: PropTypes.bool,
  isEdit: PropTypes.bool,
};

export default GeneForm;
