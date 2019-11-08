import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { createStore } from 'redux';

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

const defaultInitalState = {};

function formReducer(state = { ...defaultInitalState }, action) {
  switch (action.type) {
    case 'INITIALIZE':
      const fields = Object.keys(action.fields).reduce((result, fieldId) => {
        const { value, error } = action.fields[fieldId];
        result[fieldId] = {
          value: value,
          initialValue: value,
          error: error,
        };
        return result;
      }, {});
      return {
        fields: fields,
      };
    case 'UPDATE_FIELD':
      const { fieldId, value } = action;
      return {
        fields: {
          ...state.fields,
          [fieldId]: {
            ...state.fields[fieldId],
            value: value,
          },
        },
      };
    default:
      return state;
  }
}

const isProvenanceField = (function() {
  const provenanceFieldIds = new Set(['what', 'when', 'who', 'why', 'how']);
  return function(fieldId) {
    return provenanceFieldIds.has(fieldId);
  };
})();

function dirtySelect(state) {
  return Object.keys(state.fields)
    .filter((fieldId) => !isProvenanceField(fieldId))
    .reduce((result, fieldId) => {
      const value = (state.fields[fieldId] || {}).value || '';
      const initialValue = (state.fields[fieldId] || {}).initialValue || '';
      return result || value !== initialValue;
    }, false);
}

function getDirtyFields(fields) {
  return Object.keys(fields).reduce((result, fieldId) => {
    const value = (fields[fieldId] || {}).value || '';
    const initialValue = (fields[fieldId] || {}).initialValue || '';
    if (value !== initialValue) {
      result[fieldId] = fields[fieldId];
    }
    return result;
  }, {});
}

class BaseForm extends Component {
  constructor(props) {
    super(props);
    this.initialize(props);

    // NOTE: BaseForm cannnot have any state, because its state change
    //      will trigger render and cause the input to lose focus
  }

  componentDidUpdate(prevProps, prevState) {
    if (
      prevProps.fields !== this.props.fields ||
      JSON.stringify(prevProps.data) !== JSON.stringify(this.props.data)
    ) {
      this.initialize(this.props);
    }
  }

  initialize(props) {
    const fields = this.unpackFields(props);
    this.dataStore = this.dataStore || createStore(formReducer);
    this.dataStore.dispatch({
      type: 'INITIALIZE',
      fields: fields,
    });
    this.dataStore.subscribe(() => {
      console.log(dirtySelect(this.dataStore.getState()));
      console.log(this.dataStore.getState().fields);
    });
  }

  unpackFields = (props) => {
    function flatten(result, tree, prefix) {
      if (typeof tree === 'object' && tree !== null) {
        Object.keys(tree).reduce((result, keySegment) => {
          flatten(
            result,
            tree[keySegment],
            prefix ? `${prefix}:${keySegment}` : keySegment
          );
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
  };

  gatherFields(fieldIds, fields) {
    return fieldIds.reduce((result, fieldId) => {
      const value = fields[fieldId] && fields[fieldId].value;
      const idSegments = fieldId.split(':');
      idSegments.reduce((resultSubtree, idSegment, index) => {
        if (index < idSegments.length - 1) {
          resultSubtree[idSegment] = resultSubtree[idSegment] || {};
        } else {
          resultSubtree[idSegment] = value;
        }
        return resultSubtree[idSegment];
      }, result);
      return result;
    }, {});
  }

  getDataModified = () => {
    const fields = getDirtyFields(this.dataStore.getState().fields);
    console.log(fields);
    const dataFieldIds = Object.keys(fields).filter(
      (fieldId) => !isProvenanceField(fieldId)
    );
    if (Object.keys(dataFieldIds).length === 0) {
      return null;
    }
    const provenanceFieldIds = Object.keys(fields).filter((fieldId) =>
      isProvenanceField(fieldId)
    );
    return {
      data: this.gatherFields(dataFieldIds, fields),
      prov: this.gatherFields(provenanceFieldIds, fields),
    };
  };

  getData = () => {
    const fields = this.dataStore.getState().fields;
    const dataFieldIds = Object.keys(fields).filter(
      (fieldId) => !isProvenanceField(fieldId)
    );
    const provenanceFieldIds = Object.keys(fields).filter((fieldId) =>
      isProvenanceField(fieldId)
    );
    return {
      data: this.gatherFields(dataFieldIds, fields),
      prov: this.gatherFields(provenanceFieldIds, fields),
    };
  };

  withFieldData = (WrappedComponent, fieldId) => {
    const { disabled } = this.props;
    const dataStore = this.dataStore;

    const fieldSelect = (state) => {
      const fields = state.fields;
      if (fields && fields[fieldId]) {
        return fields[fieldId];
      } else {
        return {};
      }
    };

    class Field extends Component {
      constructor(props) {
        super(props);
        this.state = {
          value: fieldSelect(dataStore.getState()).value || '',
          error: fieldSelect(dataStore.getState()).error || '',
        };
      }

      componentDidMount() {
        this.unsubscribe = dataStore.subscribe(() => {
          const currentValue = fieldSelect(dataStore.getState()).value || '';
          const currentError = fieldSelect(dataStore.getState()).error || '';
          if (
            this.state.value !== currentValue ||
            this.state.error !== currentError
          ) {
            this.setState({
              value: currentValue,
              error: currentError,
            });
          }
        });
      }

      componentWillUnmount() {
        this.unsubscribe && this.unsubscribe();
      }

      render() {
        return (
          <WrappedComponent
            {...this.props}
            id={fieldId}
            disabled={disabled || this.props.disabled}
            value={this.state.value}
            error={Boolean(this.state.error)} //Boolean function not constructor
            helperText={this.state.error || this.props.helperText}
            onChange={(event) => {
              dataStore.dispatch({
                type: 'UPDATE_FIELD',
                fieldId: fieldId,
                value: event.target.value,
              });
            }}
          />
        );
      }
    }
    return Field;
  };

  dirtinessContext = (renderer) => {
    const dataStore = this.dataStore;

    class DirtyContext extends Component {
      constructor(props) {
        super(props);
        // work around issue https://github.com/ReactTraining/react-router/issues/5707
        this.state = {
          dirty: false, //dirtySelect(dataStore.getState()),
        };
      }

      componentDidMount() {
        this.setState(
          {
            dirty: dirtySelect(dataStore.getState()),
          },
          () => {
            this.unsubscribe = dataStore.subscribe(() => {
              const currentDirty = dirtySelect(dataStore.getState());
              console.log(`Dirty: ${currentDirty}`);
              if (this.state.dirty !== currentDirty) {
                this.setState({
                  dirty: currentDirty,
                });
              }
            });
          }
        );
      }

      componentWillUnmount() {
        this.unsubscribe && this.unsubscribe();
      }

      render() {
        return renderer({
          dirty: this.state.dirty,
        });
      }
    }
    return <DirtyContext />;
  };

  render() {
    /* render props changes causes inputs to lose focus */
    /* to get around the issue, pass getter functions instead of values */
    return this.props.children({
      withFieldData: this.withFieldData,
      dirtinessContext: this.dirtinessContext,
      getFormData: this.getData,
      getFormDataModified: this.getDataModified,
      // getFormProps: () => ({
      //   noValidate: true,
      //   autoComplete: 'off',
      // }),
      resetData: () => {
        this.initialize(this.props);
      },
    });
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
