import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  AutocompleteBase,
  AutocompleteChip,
  AutocompleteSuggestion,
  CircularProgress,
  withStyles,
  InputAdornment,
  Paper,
  TextField,
  SimpleListPagination,
} from '../../components/elements';

import { AutocompleteLoader } from '../Search';

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
        startAdornment: item ? (
          <InputAdornment position="start">
            <AutocompleteChip suggestion={item} onDelete={reset} />
          </InputAdornment>
        ) : null,
      }}
      {...other}
    />
  );
}

class GeneAutocomplete extends Component {
  itemToString = (item) => (item ? item.id : '');

  render() {
    const { classes, onChange, value, pageSize, ...otherProps } = this.props;
    return (
      <AutocompleteBase
        itemToString={this.itemToString}
        onInputValueChange={(inputValue) =>
          onChange({
            target: {
              value: inputValue,
            },
          })
        }
        defaultInputValue={value}
      >
        {({
          getInputProps,
          getItemProps,
          isOpen,
          inputValue,
          selectedItem,
          highlightedIndex,
          clearSelection,
          ...downshift
        }) => (
          <div>
            <AutocompleteLoader
              entityType="gene"
              apiPrefix="/api/gene"
              inputValue={inputValue}
              selectedValue={this.itemToString(selectedItem)}
              onSuggestionChange={(suggestions) => {
                //  if (!isOpen || isInitialLoad) {
                // set selectedItem based on matching of inputValue with suggestions, when
                // 1) suggestion loads when user isn't interacting with the field, OR
                // 2) suggestion loads is first loaded with the initial/unmodified inputValue
                const [nextSelectedItem] = suggestions.filter(
                  (item) =>
                    item['cgc-name'] === inputValue ||
                    item['sequence-name'] === inputValue ||
                    item.id === inputValue
                );
                if (nextSelectedItem) {
                  downshift.selectItem(nextSelectedItem);
                }
                //  }
              }}
            >
              {({ suggestions, isLoading }) => (
                <div className={classes.root}>
                  {renderInput({
                    fullWidth: true,
                    classes,
                    InputProps: getInputProps({
                      id: 'gene-id',
                    }),
                    item: selectedItem,
                    reset: clearSelection,
                    ...otherProps,
                  })}
                  {isLoading ? (
                    <Paper className={classes.paper} square>
                      <div className={classes.loading}>
                        <CircularProgress size={24} />
                      </div>
                    </Paper>
                  ) : (
                    <SimpleListPagination
                      items={suggestions}
                      pageSize={pageSize}
                      onPageChange={(startIndex, endIndex) => {
                        // downshift.openMenu();  // otherwise inputBlur would cause the menu to close
                        downshift.setItemCount(endIndex - startIndex);
                      }}
                    >
                      {({ pageItems, navigation }) =>
                        isOpen ? (
                          <Paper className={classes.paper} square>
                            {pageItems.map((suggestion, index) => (
                              <AutocompleteSuggestion
                                suggestion={suggestion}
                                index={index}
                                highlightedIndex={highlightedIndex}
                                selectedItem={selectedItem}
                                itemProps={getItemProps({ item: suggestion })}
                              />
                            ))}
                            {navigation}
                          </Paper>
                        ) : null
                      }
                    </SimpleListPagination>
                  )}
                </div>
              )}
            </AutocompleteLoader>
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
};

const styles = (theme) => ({
  root: {
    width: '20em',
    position: 'relative',
  },
  paper: {
    position: 'absolute',
    zIndex: 1,
    marginTop: -6 * theme.spacing.unit,
    left: 0,
    right: 0,
  },
  loading: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: theme.spacing.unit,
  },
});

export default withStyles(styles)(GeneAutocomplete);
