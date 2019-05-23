import React, { useEffect, useMemo } from 'react';
import PropTypes from 'prop-types';
import { mockFetchOrNot } from '../../mock';
import {
  extract as extractQueryString,
  parse as parseQueryString,
} from 'query-string';
import { useDataFetch } from '../../containers/Authenticate';

function getSuggestionFromMatches(match) {
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
}

export default function AutocompleteLoader({
  children,
  entityType,
  inputValue,
  selectedValue,
  onSuggestionChange,
}) {
  const url = useMemo(
    () => {
      if (entityType && inputValue) {
        return `/api/${entityType}/?pattern=${inputValue}`;
      }
      return null;
    },
    [entityType, inputValue]
  );
  const { isLoading, data, setUrl } = useDataFetch(url, {});

  const suggestions = useMemo(
    () => (data.matches || []).map(getSuggestionFromMatches),
    [data]
  );
  useEffect(
    () => {
      if (
        url &&
        suggestions.filter((item) => item.id === inputValue).length === 0
      ) {
        // don't fetch if item is already in the result, ie when selecting an item from suggestion
        setUrl(url);
      }
    },
    [url, suggestions, inputValue]
  );
  useEffect(
    () => {
      //alert(JSON.stringify(suggestions));
      onSuggestionChange && onSuggestionChange(suggestions);
    },
    [suggestions]
  );
  return children({
    suggestions: suggestions || [],
    isLoading: isLoading,
  });
}

AutocompleteLoader.propTypes = {
  entityType: PropTypes.string.isRequired,
  inputValue: PropTypes.string,
  selectedValue: PropTypes.string,
  onSuggestionChange: PropTypes.func,
};
