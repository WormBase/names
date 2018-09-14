import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  BaseForm,
  BiotypeSelect,
  Button,
  ProgressButton,
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
  TextField,
  SpeciesSelect,
  withStyles,
} from '../../components/elements';

class GeneForm extends Component {

  render() {
    const {classes, data = {}, disabled, submitted, createMode} = this.props;
    const dataNew = {
      ...data,
      'gene/species': {
        'species/latin-name': data['gene/species'] && data['gene/species']['species/latin-name'],
      }
    };
    return (
      <BaseForm data={dataNew} disabled={disabled || submitted}>
        {
          ({withFieldData, getFormData, resetData}) => {
            const CgcNameField = withFieldData(TextField, 'gene/cgc-name');
            const SequenceNameField = withFieldData(TextField, 'gene/sequence-name');
            const SpeciesSelectField = withFieldData(SpeciesSelect, 'gene/species:species/latin-name');
            const BiotypeSelectField = withFieldData(BiotypeSelect, 'gene/biotype');
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <div>
                <CgcNameField
                  label="CGC name"
                  helperText="Enter the CGC name of the gene"
                />
                <SequenceNameField
                  label="Sequence name"
                />
                <SpeciesSelectField required />
                <BiotypeSelectField
                  required={dataNew['gene/sequence-name'] || dataNew['gene/biotype']} // once a cloned gene, always a cloned gene
                  helperText={'For cloned genes, biotype is required. Otherwise, it\'s optional'}
                />
                <ReasonField
                  label="Reason"
                  helperText={createMode ? 'Why do you create this gene' : 'Why do you edit this gene?'}
                />
                <br/>
                <div className={classes.actions}>
                  <ProgressButton
                    status={submitted ? PROGRESS_BUTTON_PENDING : PROGRESS_BUTTON_READY}
                    variant="raised"
                    color="secondary"
                    onClick={() => this.props.onSubmit(getFormData())}
                    disabled={disabled}
                  >Submit</ProgressButton>
                  <Button
                    variant="raised"
                    onClick={() => {
                      resetData();
                      this.props.onCancel && this.props.onCancel();
                    }}
                    disabled={disabled}
                  >Cancel</Button>
                </div>
              </div>
            );
          }
        }
      </BaseForm>
    );
  }
}

GeneForm.propTypes = {
  classes: PropTypes.object.isRequired,
  data: PropTypes.object,
  submitted: PropTypes.bool,
  disabled: PropTypes.bool,
  onSubmit: PropTypes.func.isRequired,
  onCancel: PropTypes.func,
  createMode: PropTypes.bool,
};

GeneForm.defaultProps = {
};

const styles = (theme) => ({
  root: {
    // display: 'flex',
    // flexDirection: 'column',
  },
  actions: {
    marginTop: theme.spacing.unit * 2,
    '& > *': {
      marginRight: theme.spacing.unit,
    },
  },
});

export default withStyles(styles)(GeneForm);
