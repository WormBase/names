import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  BaseForm,
  BiotypeSelect,
  TextField,
  SpeciesSelect,
} from '../../components/elements';

class GeneForm extends Component {
  render() {
    const { withFieldData, cloned = false, dirtinessContext } = this.props;
    const CgcNameField = withFieldData(TextField, 'gene/cgc-name');
    const SequenceNameField = withFieldData(TextField, 'gene/sequence-name');
    const SpeciesSelectField = withFieldData(SpeciesSelect, 'gene/species');
    const BiotypeSelectField = withFieldData(BiotypeSelect, 'gene/biotype');
    const ReasonField = withFieldData(TextField, 'provenance/why');

    return (
      <React.Fragment>
        <CgcNameField
          label="CGC name"
          helperText="Enter the CGC name of the gene"
        />
        <SequenceNameField label="Sequence name" />
        <SpeciesSelectField required />
        <BiotypeSelectField
          required={cloned} // once a cloned gene, always a cloned gene
          helperText={
            "For cloned genes, biotype is required. Otherwise, it's optional"
          }
        />
        {dirtinessContext(({ dirty }) =>
          dirty ? (
            <ReasonField
              label="Reason"
              helperText={`Why do you edit this gene?`}
            />
          ) : null
        )}
      </React.Fragment>
    );
  }
}

GeneForm.propTypes = {
  withFieldData: PropTypes.func.isRequired,
  dirtinessContext: PropTypes.func.isRequired,
  cloned: PropTypes.bool,
};

export default GeneForm;
