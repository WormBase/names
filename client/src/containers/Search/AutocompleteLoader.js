import { useEffect, useMemo, useRef } from 'react';
import PropTypes from 'prop-types';
import { useDataFetch } from '../../containers/Authenticate';
import { useEntityTypes } from '../../containers/Entity';

export default function AutocompleteLoader({
  children,
  entityType,
  inputValue,
  selectedValue,
  onSuggestionChange,
  prefixLengthMin = 3,
}) {
  const { isLoading, data, setFetchFunc } = useDataFetch(null, {}); // can't provide fetchFunc now, because it depends on suggestions
  const suggestions = useMemo(() => data.matches || [], [data]);
  const suggestinsRef = useRef(suggestions); // for accessing the current suggestions from effect
  const { getEntityType } = useEntityTypes();
  const apiPrefix = useMemo(
    () => {
      const entityTypeConfig = getEntityType(entityType);
      return entityTypeConfig && entityTypeConfig.apiPrefix;
    },
    [getEntityType, entityType]
  );

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

      if (
        apiPrefix &&
        inputValue &&
        inputValue.length >= prefixLengthMin &&
        !inputValue.match(/^WB[a-zA-Z]*\d{0,6}$/) &&
        !resultItem
      ) {
        setFetchFunc((authorizedFetch) => {
          return authorizedFetch(`${apiPrefix}/?pattern=${inputValue}`, {
            method: 'GET',
          });
        });
      }
    },
    [entityType, apiPrefix, inputValue, setFetchFunc, prefixLengthMin]
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
  prefixLengthMin: PropTypes.number,
};
