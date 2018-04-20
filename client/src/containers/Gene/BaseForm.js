import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button, Icon, TextField } from '../../components/elements';

/*
  BaseForm intends to **centrally** track the state of the (controlled) form
  as users modifies it (https://reactjs.org/docs/forms.html#controlled-components),
  as well as passing the values back to the individual Field components as props
  for display and validation.

  `withFieldData` associates a input field with a value tracked by the state.
  `withFieldData` is a higher order component takes a input component and a identifier (fieldId),
  that identifies the value in the state object.

  The state of the form was initialy implemented as the state of the BaseForm.
  However rerender of BaseForm caused by state change makes input fields losing focus.
  To get around the problem, the current implementation gave up storing state in BaseForm,
  by store it in the FormDataStore, and use eventListeners to re-render Field component,
  without rerendering the BaseForm.

*/

class FormDataStore {
  constructor(fields) {
    this.fields = {...fields};
    this.eventListeners = [];
  }

  setEventListener = (fieldId, handler) => {
    this.eventListeners = [
      ...this.eventListeners,
      {
        fieldId: fieldId,
        eventHandler: handler,
      },
    ];
  }

  removeEventListener = (fieldId) => {
    this.eventListeners = this.eventListeners.filter((eventListener) => {
      return eventListener.fieldId !== fieldId;
    });
  }

  getUpdateFunction = (fieldId) => {
    return (value) => {
      this.fields = {
        ...this.fields,
        [fieldId]: {
          value: value,
        },
      };
      this.eventListeners.filter((eventListener) => {
        return eventListener.fieldId === fieldId;
      }).map((eventListener) => {
        eventListener.eventHandler(value);
      });
    };
  }

  getField = (fieldId) => {
    return {
      ...this.fields[fieldId],
    };
  }

  getData = () => {
    return Object.keys(this.fields).reduce(
      (result, fieldId) => {
        result[fieldId] = this.fields[fieldId] && this.fields[fieldId].value;
        return result;
      },
      {}
    );
  }

}

class BaseForm extends Component {
  constructor(props) {
    super(props);
    this.state = {
      fields: {...props.fields}
    };
    this.dataStore = new FormDataStore(this.state.fields);
  }

  componentWillReceiveProps(nextProps) {
    this.state = {
      fields: {...nextProps.fields}
    };
    this.dataStore = new FormDataStore(nextProps.fields);
  }

  withFieldData = (WrappedComponent, fieldId) => {
    const dataStore = this.dataStore;
    const {value, onChange,} = dataStore.getField(fieldId);
    class Field extends Component {
      constructor(props) {
        super(props);
        this.state = {
          ...dataStore.getField(fieldId)
        };
      }

      componentDidMount() {
        dataStore.setEventListener(fieldId, (value) => {
          this.setState({
            value: value,
            error: null,
          });
        });
      }

      componentWillUnmount() {
        dataStore.removeEventListener(fieldId);
      }

      render() {
        const updateStoreField = dataStore.getUpdateFunction(fieldId);
        return (
          <WrappedComponent
            {...this.props}
            id={fieldId}
            value={this.state.value || ''}
            error={Boolean(this.state.error)} //Boolean function not constructor
            helperText={this.state.error || this.props.helperText}
            onChange={
              (event) => {
                updateStoreField(event.target.value)
              }
            }
          />
        );
      }
    }
    return Field;
  }

  render() {
    return (
      <form noValidate autoComplete="off">
        {
          this.props.children({
            withFieldData: this.withFieldData,
            getFormData: this.dataStore.getData,
          })
        }
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
