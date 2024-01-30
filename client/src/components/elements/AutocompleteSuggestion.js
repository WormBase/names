import React from 'react';
import PropTypes from 'prop-types';
import MenuItem from '@material-ui/core/MenuItem';
import { withStyles } from '@material-ui/core/styles';
import classNames from 'classnames';

function AutocompleteSuggestion({
  classes = {},
  suggestion,
  index,
  itemProps,
  highlightedIndex,
  selectedItem,
  ...others
}) {
  const isHighlighted = highlightedIndex === index;
  const isSelected = selectedItem === suggestion;
  const className = classNames({
    [classes.menuItemSelected]: isSelected,
  });

  return (
    <MenuItem
      {...itemProps}
      key={suggestion.label}
      selected={isHighlighted}
      {...others}
      className={className}
    >
      {suggestion['cgc-name'] ||
        suggestion['sequence-name'] ||
        suggestion.name ||
        'Undefined'}{' '}
      <span className={classes.wbId}>[{suggestion.id}]</span>
    </MenuItem>
  );
}

AutocompleteSuggestion.propTypes = {
  classes: PropTypes.object.isRequired,
  highlightedIndex: PropTypes.number,
  index: PropTypes.number,
  itemProps: PropTypes.object,
  selectedItem: PropTypes.string,
  suggestion: PropTypes.shape({
    id: PropTypes.string.isRequired,
    label: PropTypes.string,
  }).isRequired,
};

const styles = (theme) => ({
  menuItemSelected: {
    fontWeight: 500,
  },
  wbId: {
    fontSize: '0.8em',
    display: 'inline-block',
    padding: `0 ${theme.spacing(0.5)}px`,
  },
});

export default withStyles(styles)(AutocompleteSuggestion);
