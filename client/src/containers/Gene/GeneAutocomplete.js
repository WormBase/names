import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  AutocompleteBase,
  withStyles,
  Chip,
  InputAdornment,
  Paper,
  MenuItem,
  TextField,
  SimpleListPagination,
} from '../../components/elements';
import GeneAutocompleteLoader from './GeneAutocompleteLoader';

function renderInput(inputProps) {
  const { InputProps, classes, ref, item, reset, ...other } = inputProps;
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
      <AutocompleteBase
        onChange={(selectItem) => onChange({
          target: {
            value: selectItem,
          },
        })}
        defaultInputValue={value}
      >
        {({getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex, clearSelection, ...downshift}) => (
          <div>
            <GeneAutocompleteLoader inputValue={inputValue}>
              {({suggestions}) => (
                <div className={classes.root}>
                  {renderInput({
                    fullWidth: true,
                    classes,
                    InputProps: getInputProps({
                      id: 'gene-id',
                    }),
                    item: selectedItem ? suggestions.filter(
                      (item) => item.id === selectedItem
                    )[0] : null,
                    reset: clearSelection,
                    ...otherProps,
                  })}
                  <SimpleListPagination
                    items={suggestions}
                    onPageChange={(startIndex, endIndex) => {
                      downshift.openMenu();  // otherwise inputBlur would cause the menu to close
                      downshift.setItemCount(endIndex - startIndex);
                    }}
                  >
                    {({pageItems, navigation}) => (
                      isOpen ? (
                        <Paper className={classes.paper} square>
                          {
                            pageItems.map((suggestion, index) => (
                              renderSuggestion({
                                suggestion,
                                index,
                                itemProps: getItemProps({item: suggestion.id}),
                                highlightedIndex,
                                selectedItem,
                              })
                            ))
                          }
                          {navigation}
                        </Paper>
                      ) : null
                    )}
                  </SimpleListPagination>
              </div>
            )}
            </GeneAutocompleteLoader>
          </div>
        )}
      </AutocompleteBase>
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
