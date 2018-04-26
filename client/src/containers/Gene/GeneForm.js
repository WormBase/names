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
    const {classes, fields, data} = this.props;
    return (
      <BaseForm data={data}>
        {
          ({withFieldData, getFormData, resetData}) => {
            const WBIdField = withFieldData(TextField, 'id');
            const CgcNameField = withFieldData(TextField, 'cgcName');
            const SequenceNameField = withFieldData(TextField, 'sequenceName');
            const SpeciesSelectField = withFieldData(SpeciesSelect, 'species');
            const BiotypeSelectField = withFieldData(BiotypeSelect, 'biotype');
            return (
              <div>
                {
                  data.id ?
                    <WBIdField
                      label="WormBase gene ID"
                      disabled={true}
                    /> :
                    null
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
                <div className={classes.actions}>
                  <Button
                    variant="raised"
                    color="secondary"
                    onClick={() => this.props.onSubmit(getFormData())}
                  >Submit</Button>
                  <Button
                    variant="raised"
                    onClick={() => resetData()}
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
  fields: PropTypes.shape({
    cgcName: PropTypes.shape({
      value: PropTypes.string,
      error: PropTypes.string,
    }),
  }),
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
  actions: {
    marginTop: theme.spacing.unit * 2,
    '& > *': {
      marginRight: theme.spacing.unit,
    },
  },
});

export default withStyles(styles)(GeneForm);
