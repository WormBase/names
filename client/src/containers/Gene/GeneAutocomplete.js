import React, { Component } from 'react';
import PropTypes from 'prop-types';
import SearchIcon from '@material-ui/icons/Search';
import {
  withStyles,
  Button,
  Chip,
  Icon,
  InputAdornment,
  Paper,
  MenuItem,
  TextField,
} from '../../components/elements';
import GeneAutocompleteBase from './GeneAutocompleteBase';

function renderInput(inputProps) {
  const { InputProps, classes, ref, item, reset, ...other } = inputProps;
  console.log(inputProps);
  return (
      <TextField
        InputProps={{
          ...InputProps,
          value: item ? '' : InputProps.value,
          disabled: item,
          inputRef: ref,
          classes: {
            root: classes.inputRoot,
          },
          startAdornment: (
            <InputAdornment position="start">
              {
                item ?
                  <Chip
                    tabIndex={-1}
                    label={`${item.label} [ID: ${item.id}]`}
                    className={classes.chip}
                    onDelete={reset}
                  /> :
                  null
              }

            </InputAdornment>
          ),
        }}
        {...other}
      />
    )
}

function renderSuggestion({ suggestion, index, itemProps, highlightedIndex, selectedItem }) {
  const isHighlighted = highlightedIndex === index;
  const isSelected = selectedItem === suggestion.id;

  return (
    <MenuItem
      {...itemProps}
      key={suggestion.label}
      selected={isHighlighted}
      component={'div'}
      style={{
        fontWeight: isSelected ? 500 : 400,
      }}
    >
      {suggestion.label}
    </MenuItem>
  );
}
renderSuggestion.propTypes = {
  highlightedIndex: PropTypes.number,
  index: PropTypes.number,
  itemProps: PropTypes.object,
  selectedItem: PropTypes.string,
  suggestion: PropTypes.shape({
    id: PropTypes.string.isRequired,
    label: PropTypes.string,
  }).isRequired,
};

class GeneAutocomplete extends Component {
  render() {
    const {classes, onChange, value, ...otherProps} = this.props;
    return (
      <GeneAutocompleteBase onChange={onChange} value={value}>
        {({getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex, suggestions, reset}) => (
          <div className={classes.root}>
            {renderInput({
              fullWidth: true,
              classes,
              InputProps: getInputProps({
                id: 'gene-id',
              }),
              item: selectedItem ? suggestions.filter(
                (item) => item.id === selectedItem,
              )[0] : null,
              reset,
              ...otherProps,
            })}
            {isOpen ? (
              <Paper className={classes.paper} square>
                {suggestions.map((suggestion, index) =>
                  renderSuggestion({
                    suggestion,
                    index,
                    itemProps: getItemProps({item: suggestion.id}),
                    highlightedIndex,
                    selectedItem,
                  }),
                )}
              </Paper>
            ) : null}
          </div>
        )}
      </GeneAutocompleteBase>
    );
  }
}

GeneAutocomplete.propTypes = {
  classes: PropTypes.object.isRequired,
  value: PropTypes.string,
  onChange: PropTypes.func,
}

const styles = (theme) => ({
  root: {
    width: '20em',
    position: 'relative',
  },
  paper: {
    position: 'absolute',
    zIndex: 1,
    marginTop: -4 * theme.spacing.unit,
    left: 0,
    right: 0,
  },
});

export default withStyles(styles)(GeneAutocomplete);
