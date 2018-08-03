import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';

class AutocompleteBase extends Component {
  constructor(props) {
    super(props);
    this.state = {
      suggestions: [],
    };
  }

  componentDidMount() {
    if (this.props.value) {
      this.loadSuggestions(this.props.value);
    }
  }

  handleInputChange = (inputValue) => {
    this.setState({
      suggestions: [],
    }, () => {
      this.loadSuggestions(inputValue);
    });
  }

  loadSuggestions = (inputValue) => {
    this.props.loadSuggestions(inputValue, (suggestions) => {
      this.setState({
        suggestions: suggestions || [],
      });
    });
  }

  handleKeyDown = (inputValue) => {
    const [selectedItem] = this.state.suggestions.filter(
      (item) => item.id === inputValue || item.label === inputValue
    );
    if (selectedItem) {
      this.setState({
        selectedItem: selectedItem.id,
        isOpen: false,
      });
    }
  }

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
    console.log(Object.keys(Downshift.stateChangeTypes));
    const {inputValue} = changes;
    switch (changes.type) {
      case Downshift.stateChangeTypes.changeInput:
        this.handleInputChange(inputValue);
        break;
      default:
        // do nothing
    }

    if (inputValue && this.props.onChange) {
      this.props.onChange({
        target: {
          value: changes.inputValue,
        },
      });
    }
  }

  render() {
    const {onChange, value} = this.props;
    return (
      <Downshift
        defaultInputValue={value}
        stateReducer={this.stateReducer}
        onStateChange={this.handleStateChange}
      >
        {({ getInputProps, inputValue, ...otherProps }) => (
          this.props.children({
            ...otherProps,
            inputValue,
            getInputProps: (inputProps) => {
              return getInputProps({
                ...inputProps,
                onKeyDown: (event) => {
                  inputProps.onKeyDown && inputProps.onKeyDown(event);
                  if (event.key === 'Enter') {
                    const [selectedItem] = this.state.suggestions.filter(
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
            suggestions: this.state.suggestions,
          })
        )}
      </Downshift>
    );
  }
}

AutocompleteBase.propTypes = {
  children: PropTypes.func.isRequired,
  onChange: PropTypes.func,
  value: PropTypes.string,
  loadSuggestions: PropTypes.func.isRequired,
}


export default AutocompleteBase;
