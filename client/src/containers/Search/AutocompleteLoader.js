import { useEffect, useMemo, useContext, useRef } from 'react';
import PropTypes from 'prop-types';
import {
  useDataFetch,
  AuthorizationContext,
} from '../../containers/Authenticate';

export default function AutocompleteLoader({
  children,
  entityType,
  apiPrefix = `/api/entity/${entityType}/`,
  inputValue,
  selectedValue,
  onSuggestionChange,
}) {
  const { authorizedFetch } = useContext(AuthorizationContext);
  const { isLoading, data, setFetchFunc } = useDataFetch(null, {}); // can't provide fetchFunc now, because it depends on suggestions
  const suggestions = useMemo(() => data.matches || [], [data]);
  const suggestinsRef = useRef(suggestions); // for accessing the current suggestions from effect

  useEffect(
    () => {
      //alert(JSON.stringify(suggestions));
      onSuggestionChange && onSuggestionChange(suggestions);
      suggestinsRef.current = suggestions;
    },
    [suggestions, onSuggestionChange]
  );

  useEffect(
    () => {
      const [resultItem] = suggestinsRef.current.filter(
        (item) => item.id === inputValue
      );

      if (apiPrefix && inputValue && !resultItem) {
        setFetchFunc(() => () => {
          return authorizedFetch(`${apiPrefix}/?pattern=${inputValue}`, {
            method: 'GET',
          });
        });
      }
    },
    [entityType, apiPrefix, inputValue, authorizedFetch, setFetchFunc]
  );

  return children({
    suggestions: suggestions || [],
    isLoading: isLoading,
  });
}

AutocompleteLoader.propTypes = {
  entityType: PropTypes.string.isRequired,
  apiPrefix: PropTypes.string.isRequired,
  inputValue: PropTypes.string,
  selectedValue: PropTypes.string,
  onSuggestionChange: PropTypes.func,
};
