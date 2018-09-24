import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Prompt } from 'react-router';

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
      }).forEach((eventListener) => {
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
    this.eventListeners.forEach((eventListener) => {
      if (eventListener.fieldId === 'ALL_FIELDS') {
        eventListener.eventHandler();
      } else {
        const field = this.fields[eventListener.fieldId];
        eventListener.eventHandler(field ? field.value : null);
      }
    });
  }

  getData = (otherFields) => {
    const fields = otherFields || this.fields;
    return Object.keys(fields).reduce(
      (result, fieldId) => {
        const value = fields[fieldId] && fields[fieldId].value;
        const idSegments = fieldId.split(':');
        idSegments.reduce((resultSubtree, idSegment, index) => {
          if (index < idSegments.length - 1) {
            resultSubtree[idSegment] = resultSubtree[idSegment] || {};
          } else {
            resultSubtree[idSegment] = value;
          }
          return resultSubtree[idSegment]
        }, result);
        return result;
      },
      {}
    );
  }

  getDataFlat = (otherFields) => {
    const fields = otherFields || this.fields;
    return Object.keys(fields).reduce(
      (result, fieldId) => {
        result[fieldId] = fields[fieldId] && fields[fieldId].value;
        return result;
      },
      {}
    );
  }

  isFormDirty = () => {
    const originalData = this.getDataFlat(this.originalFields);
    const currentData = this.getDataFlat();
    return [
      ...Object.keys(originalData),
      ...Object.keys(currentData)
    ].filter((fieldId) => !fieldId.match(/provenance\//)).reduce(
      (result, fieldId) => {
        return result || (
          (originalData[fieldId] || '') !== (currentData[fieldId] || '')
        );
      },
      false
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

    function flatten(result, tree, prefix) {
      if (typeof tree === 'object' && tree !== null) {
        Object.keys(tree).reduce((result, keySegment) => {
          flatten(result, tree[keySegment], prefix ? `${prefix}:${keySegment}` : keySegment);
          return result;
        }, result);
      } else {
        result[prefix] = tree;
      }
    }

    const fieldsFlat = {};
    flatten(fieldsFlat, props.data || {}, '');

    return {
      ...Object.keys(fieldsFlat).reduce((result, fieldId) => {
        result[fieldId] = {
          value: fieldsFlat[fieldId],
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
    const {disabled} = this.props;
    class Field extends Component {
      constructor(props) {
        super(props);
        this.state = {
          ...dataStore.getField(fieldId),
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
            disabled={disabled || this.props.disabled}
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

  withDirtyFormOnly = (WrappedComponent) => {
    const dataStore = this.dataStore;
    class DirtyFormOnly extends Component {
      constructor(props) {
        super(props);
        this.state = {
          show: false,
        };
      }

      componentDidMount() {
        dataStore.setEventListener('ALL_FIELDS', () => {
          this.setState({
            show: dataStore.isFormDirty(),
          });
        });
      }

      componentWillUnmount() {
        dataStore.removeEventListener('ALL_FIELDS');
      }

      render() {
        return this.state.show ? (
          <WrappedComponent {...this.props} />
        ) : null;
      }
    }
    return DirtyFormOnly;
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
        const originalData = dataStore.getDataFlat(fields);
        dataStore.setEventListener('ALL_FIELDS', () => {
          this.setState({
            dirty: dataStore.isFormDirty(),
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
            withDirtyFormOnly: this.withDirtyFormOnly,
            getFormData: this.dataStore.getData,
            resetData: () => {
              this.dataStore.replaceFields(this.unpackFields(this.props));
            }
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
  disabled: PropTypes.bool,
  children: PropTypes.func.isRequired,
};

export default BaseForm;
