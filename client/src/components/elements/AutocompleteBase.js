import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';

class AutocompleteBase extends Component {

  stateReducer = (state, changes) => {
    // console.log('prev state', state);
    // console.log('change', changes);
    switch (changes.type) {
      case Downshift.stateChangeTypes.blurInput:
        return {
          ...changes,
          inputValue: state.inputValue,
          isOpen: state.isOpen,
        };
      case Downshift.stateChangeTypes.mouseUp:
        return {
          ...changes,
          inputValue: state.inputValue, // prevent inputValue being cleared
        };
      default:
        return changes;
    }
  }

  handleStateChange = changes => {
    // console.log(Object.keys(Downshift.stateChangeTypes));
    switch (changes.type) {
      default:
        // do nothing
    }
  }

  render() {
    const {children, ...downshiftProps} = this.props;
    return (
      <Downshift
        stateReducer={this.stateReducer}
        onStateChange={this.handleStateChange}
        {...downshiftProps}
      >
        {({ getInputProps, inputValue, ...otherProps }) => (
          children({
            ...otherProps,
            inputValue,
            getInputProps: (inputProps) => {
              return getInputProps({
                ...inputProps,
                onFocus: (event) => {
                  inputProps.onFocus && inputProps.onFocus();
                  if (inputValue) {
                    otherProps.openMenu();
                  }
                },
              });
            },
          })
        )}
      </Downshift>
    );
  }
}

AutocompleteBase.propTypes = {
  children: PropTypes.func.isRequired,
}


export default AutocompleteBase;
