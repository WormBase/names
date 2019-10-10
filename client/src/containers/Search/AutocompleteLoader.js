import { useEffect, useMemo, useRef, useContext } from 'react';
import PropTypes from 'prop-types';
import { useDataFetch } from '../../containers/Authenticate';
import { EntityTypesContext } from '../../containers/Entity';

export default function AutocompleteLoader({
  children,
  entityType,
  inputValue,
  selectedValue,
  onSuggestionChange,
}) {
  const { isLoading, data, setFetchFunc } = useDataFetch(null, {}); // can't provide fetchFunc now, because it depends on suggestions
  const suggestions = useMemo(() => data.matches || [], [data]);
  const suggestinsRef = useRef(suggestions); // for accessing the current suggestions from effect
  const entityTypesMap = useContext(EntityTypesContext);
  const apiPrefix = useMemo(
    () => {
      const entityTypeConfig = entityTypesMap.get(entityType);
      return entityTypeConfig && entityTypeConfig['generic?']
        ? `/api/entity/${entityType}`
        : `/api/${entityType}`;
    },
    [entityTypesMap, entityType]
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

      if (apiPrefix && inputValue && !resultItem) {
        setFetchFunc((authorizedFetch) => {
          return authorizedFetch(`${apiPrefix}/?pattern=${inputValue}`, {
            method: 'GET',
          });
        });
      }
    },
    [entityType, apiPrefix, inputValue, setFetchFunc]
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
