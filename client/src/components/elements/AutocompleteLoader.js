import React from 'react';
import PropTypes from 'prop-types';
import { mockFetchOrNot } from '../../mock';
import {
  extract as extractQueryString,
  parse as parseQueryString,
} from 'query-string';

export default class AutocompleteLoader extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      inputValue: '', // track the input value to compare with the ajax response
      suggestions: [],
      isLoading: false,
    };
  }

  componentDidMount() {
    if (this.props.inputValue) {
      this.loadSuggestions(this.props.inputValue, true);
    }
  }

  componentDidUpdate(prevProps, prevState) {
    if (
      prevProps.entityType !== this.props.entityType ||
      (prevProps.inputValue !== this.props.inputValue &&
        this.props.inputValue !== this.props.selectedValue) // don't reload suggestion when selecting an item from selection
    ) {
      this.loadSuggestions(this.props.inputValue);
    }
  }

  getSuggestionFromMatches = (match) => {
    return Object.keys(match).reduce((result, key) => {
      const value = match[key];
      const [namespace, keyName] = key.split('/');
      let newPairs = keyName === 'id' ? { entityType: namespace } : {};
      return {
        ...result,
        [keyName]: value,
        ...newPairs,
      };
    }, {});
  };

  loadSuggestions = (inputValue, isInitialLoad) => {
    if (inputValue.length < 2) {
      return;
    }

    this.setState(
      {
        inputValue: inputValue,
        suggestions: [],
        isLoading: true,
      },
      () => {
        mockFetchOrNot(
          (mockFetch) => {
            const mockResult = {
              matches: [
                {
                  'gene/id': 'WB1',
                  'gene/cgc-name': 'ab',
                  'gene/sequence-name': 'AAAA.1',
                },
                {
                  'gene/id': 'WB2',
                  'gene/cgc-name': 'ac',
                  'gene/sequence-name': 'AAAC.1',
                },
              ],
            };
            return mockFetch.get(
              '*',
              new Promise((resolve) => {
                setTimeout(() => resolve(mockResult), 500);
              })
            );
          },
          () => {
            return fetch(
              `/api/${this.props.entityType}/?pattern=${inputValue}`
            );
          }
        )
          .then((response) => {
            const matchedPattern = parseQueryString(
              extractQueryString(response.url || response.mockUrl)
            ).pattern;
            return Promise.all([matchedPattern, response.json()]);
          })
          .then(([matchedPattern, content]) => {
            if (matchedPattern === this.state.inputValue) {
              // to avoid problem caused by response coming back in the wrong order
              // compare inputValue to produce suggestion with current inputValue,
              const { matches = [] } = content;
              const suggestions = matches.map(this.getSuggestionFromMatches);
              this.setState(
                {
                  suggestions,
                  isLoading: false,
                },
                () => {
                  this.props.onSuggestionChange &&
                    this.props.onSuggestionChange(suggestions, isInitialLoad);
                }
              );
            }
          })
          .catch((e) => console.log('error', e));
      }
    );
  };

  render() {
    return this.props.children({
      suggestions: this.state.suggestions || [],
      isLoading: this.state.isLoading,
    });
  }
}

AutocompleteLoader.propTypes = {
  entityType: PropTypes.string.isRequired,
  inputValue: PropTypes.string,
  selectedValue: PropTypes.string,
  onSuggestionChange: PropTypes.func,
};
