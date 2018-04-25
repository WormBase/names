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
    const {classes, fields, data, createMode} = this.props;
    return (
      <BaseForm data={data}>
        {
          ({withFieldData, getFormData}) => {
            const WBIdField = withFieldData(TextField, 'id');
            const CgcNameField = withFieldData(TextField, 'cgcName');
            const SequenceNameField = withFieldData(TextField, 'sequenceName');
            const SpeciesSelectField = withFieldData(SpeciesSelect, 'species');
            const BiotypeSelectField = withFieldData(BiotypeSelect, 'biotype');
            return (
              <div>
                {
                  createMode ?
                    null :
                    <WBIdField
                      label="WormBase gene ID"
                      disabled={true}
                    />
                }
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
                  onClick={() => this.props.onSubmit(getFormData())}
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
  createMode: PropTypes.bool,
  onSubmit: PropTypes.func.isRequired,
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
