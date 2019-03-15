import React from 'react';
import PropTypes from 'prop-types';
import MenuItem from '@material-ui/core/MenuItem';

function AutocompleteSuggestion({
  suggestion,
  index,
  itemProps,
  highlightedIndex,
  selectedItem,
  ...others
}) {
  const isHighlighted = highlightedIndex === index;
  const isSelected = selectedItem === suggestion;

  return (
    <MenuItem
      {...itemProps}
      key={suggestion.label}
      selected={isHighlighted}
      style={{
        fontWeight: isSelected ? 500 : 400,
      }}
      {...others}
    >
      {suggestion['cgc-name'] || suggestion['sequence-name'] || suggestion.id}
    </MenuItem>
  );
}

AutocompleteSuggestion.propTypes = {
  highlightedIndex: PropTypes.number,
  index: PropTypes.number,
  itemProps: PropTypes.object,
  selectedItem: PropTypes.string,
  suggestion: PropTypes.shape({
    id: PropTypes.string.isRequired,
    label: PropTypes.string,
  }).isRequired,
};

export default AutocompleteSuggestion;
