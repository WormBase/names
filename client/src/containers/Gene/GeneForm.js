import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button, Icon, TextField } from '../../components/elements';
import BaseForm from './BaseForm';

class GeneForm extends Component {

  render() {
    const {classes, fields} = this.props;
    return (
      <BaseForm>
        {
          ({withData, getAllFieldData}) => {
            const CgcNameField = withData(TextField, 'cgcName');
            const SequenceNameField = withData(TextField, 'sequenceName');
            return (
              <div>
                <CgcNameField
                  label="CGC name"
                  helperText="Enter the CGC name of the gene"
                />
                <SequenceNameField
                  label="Sequence name"
                  helperText="Sequence name"
                />
                <Button onClick={() => console.log(getAllFieldData())}>Submit</Button>
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
