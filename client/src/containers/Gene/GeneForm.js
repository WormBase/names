import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  Button,
  TextField,
  SpeciesSelect,
  BiotypeSelect,
} from '../../components/elements';

import BaseForm from './BaseForm';

class GeneForm extends Component {

  render() {
    const {classes, data, disabled} = this.props;
    return (
      <BaseForm data={data} disabled={disabled}>
        {
          ({withFieldData, getFormData, resetData}) => {
            const CgcNameField = withFieldData(TextField, 'gene/cgc-name');
            const SequenceNameField = withFieldData(TextField, 'gene/sequence-name');
            const SpeciesSelectField = withFieldData(SpeciesSelect, 'gene/species:species/latin-name');
            const BiotypeSelectField = withFieldData(BiotypeSelect, 'gene/biotype');
            return (
              <div>
                <CgcNameField
                  label="CGC name"
                  helperText="Enter the CGC name of the gene"
                />
                <SequenceNameField
                  label="Sequence name"
                />
                <SpeciesSelectField />
                <BiotypeSelectField />
                <br/>
                {
                  disabled ? null : (
                    <div className={classes.actions}>
                      <Button
                        variant="raised"
                        color="secondary"
                        onClick={() => this.props.onSubmit(getFormData())}
                      >Submit</Button>
                      <Button
                        variant="raised"
                        onClick={() => {
                          resetData();
                          this.props.onCancel && this.props.onCancel();
                        }}
                      >Cancel</Button>
                    </div>
                  )
                }
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
  data: PropTypes.any,
  onSubmit: PropTypes.func.isRequired,
  onCancel: PropTypes.func,
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
