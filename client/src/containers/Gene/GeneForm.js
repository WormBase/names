import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  Button,
  Icon,
  MenuItem,
  TextField,
  SpeciesSelect,
  BiotypeSelect,
} from '../../components/elements';

import BaseForm from './BaseForm';

class GeneForm extends Component {

  render() {
    const {classes, fields} = this.props;
    return (
      <BaseForm>
        {
          ({withFieldData, getFormData}) => {
            const CgcNameField = withFieldData(TextField, 'cgcName');
            const SequenceNameField = withFieldData(TextField, 'sequenceName');
            const SpeciesSelectField = withFieldData(SpeciesSelect, 'sequence');
            const BiotypeSelectField = withFieldData(BiotypeSelect, 'biotype');
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
                <Button
                  variant="raised"
                  color="secondary"
                  onClick={() => console.log(getFormData())}
                >Submit</Button>
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
  fields: PropTypes.shape({
    cgcName: PropTypes.shape({
      value: PropTypes.string,
      error: PropTypes.string,
    }),
  }),
};

GeneForm.defaultProps = {
  fields: {},
};

const styles = (theme) => ({
  root: {
    // display: 'flex',
    // flexDirection: 'column',
  },
});

export default withStyles(styles)(GeneForm);
