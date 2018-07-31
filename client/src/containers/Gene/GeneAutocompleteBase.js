import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import { extract as extractQueryString, parse as parseQueryString} from 'query-string';
import { AutocompleteBase } from '../../components/elements';

export default class GeneAutocompleteBase extends Component {
  constructor(props) {
    super(props);
    this.state = {
      inputValue:'',  // track the input value to compare with the ajax response
    };
  };

  loadSuggestions = (inputValue, callback) => {
    if (inputValue.length < 2) {
      return;
    }

    this.setState({
      inputValue: inputValue,
    }, () => {
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
        let suggestions;
        if (matchedPattern === this.state.inputValue) {
          // to avoid problem caused by response coming back in the wrong order
          // compare inputValue to produce suggestion with current inputValue,
          const {matches} = content;
          suggestions = matches.map((item) => ({
            id: item['gene/id'],
            label: item['gene/cgc-name'] || item['gene/sequence-name'] || item['gene/id'],
          }));
        } else {
          suggestions = [];
        }
        callback(suggestions);
      }).catch((e) => console.log('error', e));
    });
  }

  render() {
    return <AutocompleteBase loadSuggestions={this.loadSuggestions} {...this.props} />;
  }
};
