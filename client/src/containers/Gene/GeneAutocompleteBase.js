import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';
import { mockFetchOrNot } from '../../mock';
import { extract as extractQueryString, parse as parseQueryString} from 'query-string';

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

  componentDidMount() {
    if (this.state.inputValue) {
      this.loadSuggestions(this.state.inputValue);
    }
  }

  handleInputChange = (event) => {
    const inputValue = event.target.value;
    this.setState({
      inputValue: inputValue,
      selectedItem: inputValue,
      suggestions: [],
    }, () => {
      this.loadSuggestions(inputValue);
    });
  }

  loadSuggestions = (inputValue) => {
    if (inputValue.length < 2) {
      return;
    }

    mockFetchOrNot(
      (mockFetch) => {
        const mockResult = {
          matches: [
            {
              'gene/id': 'WB1',
              'gene/cgc-name': 'ab',
              'gene/sequence-name': 'AAAA.1'
            },
            {
              'gene/id': 'WB2',
              'gene/cgc-name': 'ac',
              'gene/sequence-name': 'AAAC.1'
            },
          ],
        };
        return mockFetch.get('*', new Promise((resolve) => {
          setTimeout(() => resolve(mockResult), 500);
        }));
      },
      () => {
        return fetch(`/api/gene/?pattern=${inputValue}`);
      },
    ).then((response) => {
      const matchedPattern = parseQueryString(extractQueryString(response.url || response.mockUrl)).pattern;
      return Promise.all([matchedPattern, response.json()]);
    }).then(([matchedPattern, content]) => {
      if (matchedPattern === this.state.inputValue) {
        // to avoid problem caused by response coming back in the wrong order
        // compare inputValue to produce suggestion with current inputValue,
        const {matches} = content;
        this.setState({
          suggestions: matches.map((item) => ({
            id: item['gene/id'],
            label: item['gene/cgc-name'] || item['gene/sequence-name'] || item['gene/id'],
          })),
        });
      }
    }).catch((e) => console.log('error', e));
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

    isOpen = type === Downshift.stateChangeTypes.blurInput ?
      this.state.isOpen : isOpen;

    this.setState({
      selectedItem,
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
        {({ getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex, setItemCount }) => (
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
            setItemCount,
            //handleInputChange: this.handleInputChange,
            suggestions: this.state.suggestions,
            reset: () => {
              this.setState({
                suggestions: [],
                inputValue: '',
                selectedItem: null,
                isOpen: false,
              });
            }
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
