import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button, Icon, TextField } from '../../components/elements';

class FormDataStore {
  constructor(fields) {
    this.fields = {...fields};
    this.eventListeners = [];
  }

  // setEventListener = (fieldId, handler) => {
  //   this.fields[fieldId] = this.fields[fieldId] || {};
  //   this.fields[fieldId].changeHandler = handler;
  // }
  //
  // removeEventListener = (fieldId) => {
  //   if (this.fields[fieldId]) {
  //       this.fields[fieldId].changeHandler = null;
  //   }
  // }

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

  getFieldUpdater = (fieldId) => {
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

  getAllFields = () => {
    return {
      ...this.fields,
    };
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

  componentDidMount() {

  }

  componentWillReceiveProps(nextProps) {
    this.state = {
      fields: {...nextProps.fields}
    };
    this.dataStore = new FormDataStore(nextProps.fields);
  }

  componentWillUnmount() {
  }

  withData = (WrappedComponent, fieldId) => {
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
          });
        });
      }

      componentWillUnmount() {
        dataStore.removeEventListener(fieldId);
      }

      render() {
        return (
          <WrappedComponent
            {...this.props}
            id={fieldId}
            value={this.state.value || ''}
            onChange={
              (event) => {
                dataStore.getFieldUpdater(fieldId)(event.target.value)
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
        {this.props.children({
          withData: this.withData,
          getAllFieldData: this.dataStore.getAllFields,
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
