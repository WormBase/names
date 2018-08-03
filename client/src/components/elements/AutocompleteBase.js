import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';

class AutocompleteBase extends Component {

  stateReducer = (state, changes) => {
    console.log(state);
    console.log(changes);
    switch (changes.type) {
      case Downshift.stateChangeTypes.blurInput:
        return {
          ...changes,
          inputValue: state.inputValue,
          isOpen: state.isOpen, // prevent menu from being closed when input blur
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
    const {inputValue} = changes;
    switch (changes.type) {
      case Downshift.stateChangeTypes.changeInput:
        if (this.props.onInputChange) {
          this.props.onInputChange(inputValue)
        }
        break;
      default:
        // do nothing
    }
  }

  render() {
    const {onInputChange, children, ...downshiftProps} = this.props;
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
                onKeyDown: (event) => {
                  inputProps.onKeyDown && inputProps.onKeyDown(event);
                  if (event.key === 'Enter') {
                    const [selectedItem] = this.props.suggestions.filter(
                      (item) => item.id === inputValue || item.label === inputValue
                    );
                    if (selectedItem) {
                      otherProps.selectItem(selectedItem.id);
                    }
                  }
                },
                onFocus: (event) => {
                  inputProps.onFocus && inputProps.onFocus();
                  if (inputValue) {
                    otherProps.openMenu();
                  }
                },
              });
            },
            suggestions: this.props.suggestions,
          })
        )}
      </Downshift>
    );
  }
}

AutocompleteBase.propTypes = {
  children: PropTypes.func.isRequired,
  onInputChange: PropTypes.func,
  suggestions: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string,
      label: PropTypes.strig,
    }),
  ).isRequired,
}


export default AutocompleteBase;
