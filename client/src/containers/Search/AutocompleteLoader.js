import { useEffect, useMemo, useContext, useRef } from 'react';
import PropTypes from 'prop-types';
import {
  useDataFetch,
  AuthorizationContext,
} from '../../containers/Authenticate';

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
  const { authorizedFetch } = useContext(AuthorizationContext);
  const { isLoading, data, setFetchFunc } = useDataFetch(null, {}); // can't provide fetchFunc now, because it depends on suggestions
  const suggestions = useMemo(
    () => (data.matches || []).map(getSuggestionFromMatches),
    [data]
  );
  const suggestinsRef = useRef(suggestions); // for accessing the current suggestions from effect

  useEffect(
    () => {
      //alert(JSON.stringify(suggestions));
      onSuggestionChange && onSuggestionChange(suggestions);
      suggestinsRef.current = suggestions;
    },
    [suggestions]
  );

  useEffect(
    () => {
      const [resultItem] = suggestinsRef.current.filter(
        (item) => item.id === inputValue
      );

      if (entityType && inputValue && !resultItem) {
        setFetchFunc(() => () => {
          return authorizedFetch(`/api/${entityType}/?pattern=${inputValue}`, {
            method: 'GET',
          });
        });
      }
    },
    [entityType, inputValue]
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
