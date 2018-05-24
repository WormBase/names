import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';
import { mockFetchOrNot } from '../../mock';

class GeneAutocompleteBase extends Component {
  constructor(props) {
    super(props);
    this.state = {
      suggestions: [],
      inputValue: '',
      selectedItem: null,
      isOpen: false,
    };
  }

  static getDerivedStateFromProps(nextProps, prevState) {
    return {
      selectedItem: nextProps.value,
      inputValue: nextProps.value,
    };
  }

  handleInputChange = (event) => {
    const inputValue = event.target.value;
    this.setState({
      inputValue: inputValue,
      selectedItem: inputValue,
      suggestions: [],
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', {
            inputValue: inputValue,
            suggestions: [
              {
                id: 'WB1',
                label: 'ab',
              },
              {
                id: 'WB2',
                label: 'ac',
              },
            ],
          });
        },
        () => {
          return fetch('/api/search/gene');
        },
        true
      ).then((response) => response.json()).then((content) => {
        if (content.inputValue === this.state.inputValue) {
          // to avoid problem caused by response coming back in the wrong order

          // compare inputValue to produce suggestion with current inputValue,
          const {suggestions} = content;
          // const [selectedItem] = suggestions.filter((item) => item.id === this.state.inputValue || item.label === this.state.inputValue);
          // if (selectedItem) {
          //   this.setState({
          //     suggestions: suggestions,
          //     selectedItem: selectedItem.id,
          //   });
          // } else {
            this.setState({
              suggestions: suggestions,
            });
          // }
        }
      }).catch((e) => console.log('error', e));
    });
  }

  handleKeyDown = (event) => {
    if (event.key === 'Enter') {
      const [selectedItem] = this.state.suggestions.filter(
        (item) => item.id === this.state.inputValue || item.label === this.state.inputValue
      );
      if (selectedItem) {
        this.setState({
          selectedItem: selectedItem.id,
          isOpen: false,
        });
      }
    }
  }

  changeHandler = selectedItem => {
    this.setState({
      selectedItem,
      isOpen: false,
    }, () => {
      if (this.props.onChange) {
        this.props.onChange({
          target: {
            value: selectedItem,
          },
        })
      }
    });
  }

  stateChangeHandler = changes => {
    console.log(changes);
    let {
      selectedItem = this.state.selectedItem,
      isOpen = this.state.isOpen,
      inputValue = this.state.inputValue,
      type,
    } = changes;
    isOpen = type === Downshift.stateChangeTypes.mouseUp ? this.state.isOpen : isOpen;
    this.setState({
      selectedItem: selectedItem,
      isOpen,
      inputValue,
    }, () => {
      if (changes.inputValue && this.props.onChange) {
        this.props.onChange({
          target: {
            value: inputValue,
          },
        });
      }
    });
  }

  render() {
    const {onChange, value, ...otherProps} = this.props;
    return (
      <Downshift
        selectedItem={this.state.selectedItem}
  //      itemToString={(item) => item ? item.id : ''}
        isOpen={this.state.isOpen}
        inputValue={this.state.inputValue}
        onChange={this.changeHandler}
        onStateChange={this.stateChangeHandler}
      >
        {({ getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex }) => (
          this.props.children({
            getItemProps,
            getInputProps: (inputProps) => {
              return getInputProps({
                ...inputProps,
                onChange: (event) => {
                  inputProps.onChange && inputProps.onChange(event);
                  this.handleInputChange(event);
                },
                onKeyDown: (event) => {
                  inputProps.onKeyDown && inputProps.onKeyDown(event);
                  this.handleKeyDown(event);
                },
                onBlur: (event) => {
                  inputProps.onBlur && inputProps.onBlur();
                  this.setState({
                    isOpen: false,
                  });
                },
                onFocus: (event) => {
                  inputProps.onFocus && inputProps.onFocus();
                  if (this.state.inputValue) {
                    this.setState({
                      isOpen: true,
                    });
                  }
                },
              });
            },
            isOpen,
            inputValue,
            selectedItem,
            highlightedIndex,
            //handleInputChange: this.handleInputChange,
            suggestions: this.state.suggestions,
          })
        )}
      </Downshift>
    );
  }
}

GeneAutocompleteBase.propTypes = {
  children: PropTypes.func.isRequired,
  onChange: PropTypes.func,
  value: PropTypes.string,
}


export default GeneAutocompleteBase;
