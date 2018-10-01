import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import SearchIcon from '@material-ui/icons/Search';
import CancelIcon from '@material-ui/icons/Cancel';
import {
  AutocompleteBase,
  withStyles,
  Chip,
  IconButton,
  InputAdornment,
  Paper,
  MenuItem,
  TextField,
  SimpleListPagination,
} from '../../components/elements';
import GeneAutocompleteLoader from './GeneAutocompleteLoader';

function renderInput(inputProps) {
  const { InputProps, classes, ref, item, reset, ...other } = inputProps;
  console.log('item');
  console.log(item);
  return (
    <TextField
      InputProps={{
        ...InputProps,
        inputRef: ref,
        classes: {
          root: classes.inputRoot,
          input:  item ? classes.inputDisabled : '',
        },
        value: item ? '' : InputProps.value,
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon className={classes.searchIcon} />
            {
              item ?
                <Chip
                  tabIndex={-1}
                  label={`${item.label} [${item.id}]`}
                  className={classes.chip}
                /> :
                null
            }
          </InputAdornment>
        ),
        endAdornment: (
          <InputAdornment position="end">
            <IconButton onClick={reset}>
              <CancelIcon />
            </IconButton>
          </InputAdornment>
        ),
      }}
      {...other}
    />
  );
}

function renderSuggestion({ suggestion, index, itemProps, highlightedIndex, selectedItem }) {
  const isHighlighted = highlightedIndex === index;
  const isSelected = selectedItem === suggestion.id;

  return (
    <MenuItem
      {...itemProps}
      key={suggestion.label}
      selected={isHighlighted}
      component={({...props}) => <Link to={`/gene/id/${suggestion.id}`} {...props} />}
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

class GeneSearchBox extends Component {
  render() {
    const {classes, history} = this.props;
    return (
      <AutocompleteBase>
        {({getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex, ...downshift}) => (
          <div>
            <GeneAutocompleteLoader inputValue={inputValue} selectedValue={selectedItem}>
              {({suggestions}) => (
                <div className={classes.root}>
                  {renderInput({
                    fullWidth: true,
                    classes,
                    item: selectedItem ? suggestions.filter(
                      (item) => item.id === selectedItem
                    )[0] : null,
                    reset: downshift.clearSelection,
                    InputProps: getInputProps({
                      placeholder: 'Search a gene...',
                      id: 'gene-search-box',
                      onKeyDown: event => {
                        if (event.key === 'Enter') {

                          let id;

                          if (highlightedIndex || highlightedIndex === 0) {
                            const highlightedSuggestion = suggestions[highlightedIndex];
                            if (highlightedSuggestion) {
                              id = highlightedSuggestion.id;
                            }
                          }

                          if (!id) {
                            const [nextSelectedItem] = suggestions.filter(
                              (item) => item.id === inputValue || item.label === inputValue
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

GeneSearchBox.propTypes = {
  classes: PropTypes.object.isRequired,
  value: PropTypes.string,
  onChange: PropTypes.func,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
}

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

export default withRouter(withStyles(styles)(GeneSearchBox));
