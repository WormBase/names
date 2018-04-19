import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button, Icon, TextField } from '../../components/elements';
import BaseForm from './BaseForm';

class GeneForm extends Component {
  constructor(props) {
    super(props);
    this.state = {
      fields: Object.keys(props.fields).reduce((result, fieldId) => {
        const field = props.fields[fieldId];
        return {
          ...result,
          [fieldId]: {
            ...field,
            id: fieldId,
          },
        }
      }, {}),
    }
  }

  handleChange = (fieldId) => {
    return (event) => {
      const value = event.target.value;
      this.setState((prevState) => ({
        fields: {
          ...prevState.fields,
          [fieldId]: value,
        }
      }));
    };
  }

  render() {
    const {classes} = this.props;
    const {fields} = this.state;
    return (
      <BaseForm>
        {
          ({withData}) => {
            const CgcNameField = withData(TextField, 'cgcName');
            return (
              <CgcNameField
                label="CGC Name"
                helperText="Enter the CGC name of the gene"
              />
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
