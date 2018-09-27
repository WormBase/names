import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import SearchIcon from '@material-ui/icons/Search';
import {
  withStyles,
  InputAdornment,
  Paper,
  MenuItem,
  TextField,
  SimpleListPagination,
} from '../../components/elements';
import GeneAutocompleteBase from './GeneAutocompleteBase';

function renderInput(inputProps) {
  const { InputProps, classes, ref, ...other } = inputProps;
  return (
    <TextField
      InputProps={{
        ...InputProps,
        inputRef: ref,
        classes: {
          root: classes.inputRoot,
        },
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon />
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
      <GeneAutocompleteBase>
        {({getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex, suggestions, setItemCount}) => (
          <div className={classes.root}>
            {renderInput({
              fullWidth: true,
              classes,
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
                      history.push(`/gene/id/${id}`);
                    }
                  }
                },
              }),
            })}
            <SimpleListPagination
              items={suggestions}
              onPageChange={(startIndex, endIndex) => setItemCount(endIndex - startIndex)}
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
      </GeneAutocompleteBase>
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
  },
  paper: {
    position: 'absolute',
    zIndex: 1,
    marginTop: -1 * theme.spacing.unit,
    left: 0,
    right: 0,
  },
});

export default withRouter(withStyles(styles)(GeneSearchBox));
