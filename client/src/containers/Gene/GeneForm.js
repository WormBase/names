import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  BiotypeSelect,
  TextField,
  SpeciesSelect,
} from '../../components/elements';

class GeneForm extends Component {
  render() {
    const { withFieldData, cloned = false } = this.props;
    const CgcNameField = withFieldData(TextField, 'gene/cgc-name');
    const SequenceNameField = withFieldData(TextField, 'gene/sequence-name');
    const SpeciesSelectField = withFieldData(SpeciesSelect, 'gene/species');
    const BiotypeSelectField = withFieldData(BiotypeSelect, 'gene/biotype');

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
      </React.Fragment>
    );
  }
}

GeneForm.propTypes = {
  withFieldData: PropTypes.func.isRequired,
  cloned: PropTypes.bool,
};

export default GeneForm;
