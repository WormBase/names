import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button, Icon, TextField } from '../../components/elements';

class BaseForm extends Component {
  constructor(props) {
    super(props);
    this.state = {
      fields: {...props.fields}
    };
  }

  handleChange = (fieldId) => {
    return (event) => {
      const value = event.target.value;
      this.setState((prevState) => ({
        fields: {
          ...prevState.fields,
          [fieldId]: {
            value: value,
          },
        },
        focusField: fieldId,
      }));
    };
  }

  withData = (WrappedComponent, fieldId) => {
    return (props) => {
      return (
        <WrappedComponent
          {...props}
          id={fieldId}
          value={this.state.fields[fieldId] ? this.state.fields[fieldId].value : null}
          onChange={this.handleChange(fieldId)}
          inputRef={element => this.inputElement = element}
        />
      );
    }
  }

  render() {
    return (
      <form noValidate autoComplete="off">
        {this.props.children({
          withData: this.withData,
        })}
      </form>
    );
  }
}

BaseForm.propTypes = {
  classes: PropTypes.object.isRequired,
  fields: PropTypes.objectOf({
    value: PropTypes.string,
    error: PropTypes.string,
  }),
  children: PropTypes.func.isRequired,
};

export default BaseForm;
