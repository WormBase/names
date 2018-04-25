import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Prompt } from 'react-router';
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
    this.originalFields = {...fields};
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
        return eventListener.fieldId === fieldId || eventListener.fieldId === 'ALL_FIELDS';
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

  replaceFields = (fields) => {
    this.fields = {...fields};
    this.eventListeners.map((eventListener) => {
      if (eventListener.fieldId === 'ALL_FIELDS') {
        eventListener.eventHandler();
      } else {
        eventListener.eventHandler(this.fields[eventListener.fieldId].value);
      }
    });
  }

  getData = (otherFields) => {
    const fields = otherFields || this.fields;
    return Object.keys(fields).reduce(
      (result, fieldId) => {
        result[fieldId] = fields[fieldId] && fields[fieldId].value;
        return result;
      },
      {}
    );
  }

}

class BaseForm extends Component {
  constructor(props) {
    super(props);
    const fields = this.unpackFields(props);
    this.dataStore = new FormDataStore(fields);
  }

  unpackFields = (props) => {
    return {
      ...Object.keys(props.data || {}).reduce((result, fieldId) => {
        result[fieldId] = {
          value: props.data[fieldId],
          error: null,
        };
        return result;
      }, {}),
      ...props.fields,
    };
  }

  componentDidUpdate(prevProps, prevState) {
    if (
      prevProps.fields !== this.props.fields ||
      prevProps.data !== this.props.data
    ) {
      const fields = this.unpackFields(this.props);
      this.dataStore.replaceFields(fields);
    }
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

  renderDirtyFormPrompt(fields) {
    const dataStore = this.dataStore;
    class DirtyFormPrompt extends Component {
      constructor(props) {
        super(props);
        this.state = {
          dirty: false,
        };
      }

      componentDidMount() {
        const originalData = dataStore.getData(fields);
        dataStore.setEventListener('ALL_FIELDS', () => {
          const currentData = dataStore.getData();
          const isDirty = [
            ...Object.keys(originalData),
            ...Object.keys(currentData)
          ].reduce(
            (result, fieldId) => {
              return result || (
                (originalData[fieldId] || '') !== (currentData[fieldId] || '')
              );
            },
            false
          );
          this.setState({
            dirty: isDirty,
          });
        });
      }

      componentWillUnmount() {
        dataStore.removeEventListener('ALL_FIELDS');
      }

      render() {
        return (
          <Prompt
            when={this.state.dirty}
            message="Form contains unsubmitted content, which will be lost when you leave. Are you sure you want to leave?"
          />
        );
      }
    }

    return <DirtyFormPrompt />;
  }

  render() {
    const dirtyFormPrompt = this.renderDirtyFormPrompt(this.unpackFields(this.props));
    return (
      <form noValidate autoComplete="off">
        {dirtyFormPrompt}
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
  fields: PropTypes.objectOf({
    value: PropTypes.any,
    error: PropTypes.string,
  }),
  data: PropTypes.objectOf(PropTypes.any), // alternative to fields, but the value is simply the value, instead of a map of value and error
  children: PropTypes.func.isRequired,
};

export default BaseForm;
