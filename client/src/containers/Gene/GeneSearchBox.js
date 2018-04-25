import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';
import SearchIcon from '@material-ui/icons/Search';
import { Link, withRouter } from 'react-router-dom';
import {
  withStyles,
  Button,
  Icon,
  InputAdornment,
  Paper,
  MenuItem,
  TextField,
} from '../../components/elements';
import { mockFetchOrNot } from '../../mock';


function renderInput(inputProps) {
  const { InputProps, classes, ref, history, match, location, ...other } = inputProps;
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
        onKeyDown: event => {
          if (event.key === 'Enter') {
            // Prevent Downshift's default 'Enter' behavior.
            event.preventDownshiftDefault = true;
            history.push(`/gene/id/${InputProps.value}`);
          }
        },
      }}
      {...other}
    />
  );
}

function renderSuggestion({ suggestion, index, itemProps, highlightedIndex, selectedItem }) {
  const isHighlighted = highlightedIndex === index;
  const isSelected = (selectedItem || '').indexOf(suggestion.label) > -1;

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
  constructor(props) {
    super(props);
    this.state = {
      suggestions: [],
      query: null,
    };
  }

  handleQueryChange(event) {
    const query = event.target.value;
    this.setState({
      query: query,
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', {
            query: query,
            suggestions: [
              {
                id: 'WB1',
                label: 'ab',
              },
              {
                id: 'WB2',
                label: 'ac',
              },
            ],
          });
        },
        () => {
          return fetch('/api/search/gene');
        },
        true
      ).then((response) => response.json()).then((content) => {
        if (content.query === this.state.query) {
          // compare query to produce suggestion with current query,
          // to avoid problem caused by response coming back in the wrong order
          this.setState({
            suggestions: content.suggestions,
          });
        }
      }).catch((e) => console.log('error', e));
    });
  }

  render() {
    const {classes} = this.props;
    return (
      <Downshift>
        {({ getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex }) => (
          <div className={classes.root}>
            {withRouter(renderInput)({
              fullWidth: true,
              classes,
              InputProps: getInputProps({
                placeholder: 'Search a gene...',
                id: 'gene-search-box',
                onChange: (event) => this.handleQueryChange(event),
              }),
            })}
            {isOpen ? (
              <Paper className={classes.paper} square>
                {this.state.suggestions.map((suggestion, index) =>
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
      </Downshift>
    );
  }
}

GeneSearchBox.propTypes = {
  classes: PropTypes.object.isRequired,
}

const styles = (theme) => ({
  root: {
    width: '20em',
    position: 'relative',
  },
  paper: {
    position: 'absolute',
    zIndex: 1,
    marginTop: -1 * theme.spacing.unit,
    left: 0,
    right: 0,
  },
});

export default withStyles(styles)(GeneSearchBox);