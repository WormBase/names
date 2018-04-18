import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button, Icon, TextField } from '../../components/elements';

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
      <form className={classes.root} noValidate autoComplete="off">
        <div>
          <TextField
            id="cgcName"
            label="CGC Name"
            helperText="Enter the CGC name of the gene"
            value={fields.cgcName ? fields.cgcName : null}
            onChange={this.handleChange('cgcName')}
          />
        </div>
        <div>
          <TextField
            id="cgcName"
            label="CGC Name"
            helperText="Enter the CGC name of the gene"
            value={fields.cgcName ? fields.cgcName : null}
            onChange={this.handleChange('cgcName')}
          />
        </div>
      </form>
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
