import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';

import { withStyles } from '@material-ui/core';
import SearchIcon from '@material-ui/icons/Search';
import CancelIcon from '@material-ui/icons/Cancel';
import IconButton from '@material-ui/core/IconButton';
import InputAdornment from '@material-ui/core/InputAdornment';
import Paper from '@material-ui/core/Paper';

import AutocompleteBase from './AutocompleteBase';
import AutocompleteChip from './AutocompleteChip';
import AutocompleteLoader from './AutocompleteLoader';
import AutocompleteSuggestion from './AutocompleteSuggestion';
import SimpleListPagination from './SimpleListPagination';
import TextField from './TextField';

function renderInput(inputProps) {
  const { InputProps, classes, ref, item, reset, ...other } = inputProps;
  return (
    <TextField
      InputProps={{
        ...InputProps,
        inputRef: ref,
        classes: {
          root: classes.inputRoot,
          input: item ? classes.inputDisabled : '',
        },
        value: item ? '' : InputProps.value,
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon className={classes.searchIcon} />
            {item ? <AutocompleteChip suggestion={item} /> : null}
          </InputAdornment>
        ),
        endAdornment: (
          <InputAdornment position="end">
            <IconButton onClick={reset} disabled={!item && !InputProps.value}>
              <CancelIcon />
            </IconButton>
          </InputAdornment>
        ),
      }}
      {...other}
    />
  );
}

class SearchBox extends Component {
  render() {
    const { classes, entityType = 'gene', history } = this.props;
    return (
      <AutocompleteBase itemToString={(item) => (item ? item.id : '')}>
        {({
          getInputProps,
          getItemProps,
          isOpen,
          inputValue,
          selectedItem,
          highlightedIndex,
          ...downshift
        }) => (
          <div className={classes.root}>
            <AutocompleteLoader
              entityType={entityType}
              inputValue={inputValue}
              selectedValue={selectedItem && selectedItem.id}
            >
              {({ suggestions }) => (
                <div>
                  {renderInput({
                    fullWidth: true,
                    classes,
                    item: selectedItem,
                    reset: downshift.clearSelection,
                    InputProps: getInputProps({
                      placeholder: `Search a ${entityType}...`,
                      id: `${entityType}-search-box`,
                      onKeyDown: (event) => {
                        if (event.key === 'Enter') {
                          let id;

                          if (highlightedIndex || highlightedIndex === 0) {
                            const highlightedSuggestion =
                              suggestions[highlightedIndex];
                            if (highlightedSuggestion) {
                              id = highlightedSuggestion.id;
                            }
                          }

                          if (!id) {
                            const [nextSelectedItem] = suggestions.filter(
                              (item) => {
                                return Object.keys(item).reduce(
                                  (matchAny, key) =>
                                    matchAny || item[key] === inputValue
                                );
                              }
                            );
                            id = nextSelectedItem && nextSelectedItem.id;
                          }

                          if (!id) {
                            id = inputValue;
                          }

                          if (id) {
                            // ignore empty input
                            downshift.closeMenu();
                            history.push(`/gene/id/${id}`);
                          }
                        }
                      },
                    }),
                  })}
                  <SimpleListPagination
                    items={suggestions}
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
                              component={({ ...props }) => (
                                <Link
                                  to={`/gene/id/${suggestion.id}`}
                                  {...props}
                                />
                              )}
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
                </div>
              )}
            </AutocompleteLoader>
          </div>
        )}
      </AutocompleteBase>
    );
  }
}

SearchBox.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string,
  value: PropTypes.string,
  onChange: PropTypes.func,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

const styles = (theme) => ({
  root: {
    width: '20em',
    position: 'relative',
  },
  inputRoot: {
    backgroundColor: theme.palette.common.white,
    justifyContent: 'space-between',
  },
  inputDisabled: {
    display: 'none',
  },
  paper: {
    position: 'absolute',
    zIndex: 1,
    marginTop: -1 * theme.spacing.unit,
    left: 0,
    right: 0,
  },
  searchIcon: {
    paddingLeft: theme.spacing.unit,
  },
  chip: {
    height: theme.spacing.unit * 3,
    marginLeft: theme.spacing.unit,
  },
});

export default withRouter(withStyles(styles)(SearchBox));
