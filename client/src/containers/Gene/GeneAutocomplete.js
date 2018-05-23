import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Downshift from 'downshift';
import SearchIcon from '@material-ui/icons/Search';
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

class GeneSearchBox extends Component {
  constructor(props) {
    super(props);
    this.state = {
      suggestions: [],
      inputValue: null,
      selectedItem: null,
      isOpen: false,
    };
  }

  static getDerivedStateFromProps(nextProps, prevState) {
    return {
      selectedItem: nextProps.value,
      inputValue: nextProps.value,
    };
  }

  handleInputChange = (event) => {
    const inputValue = event.target.value;
    this.setState({
      inputValue: inputValue,
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', {
            inputValue: inputValue,
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
        if (content.inputValue === this.state.inputValue) {
          // compare inputValue to produce suggestion with current inputValue,
          // to avoid problem caused by response coming back in the wrong order
          const {suggestions} = content;
          const [selectedItem] = suggestions.filter((item) => item.id === this.state.inputValue || item.label === this.state.inputValue);
          if (selectedItem) {
            this.setState({
              suggestions: suggestions,
              selectedItem: selectedItem.id,
            });
          } else {
            this.setState({
              suggestions: suggestions,
            });
          }
        }
      }).catch((e) => console.log('error', e));
    });
  }

  changeHandler = selectedItem => {
    this.setState({
      selectedItem,
      isOpen: false,
    }, () => {
      if (this.props.onChange) {
        this.props.onChange({
          target: {
            value: selectedItem,
          },
        })
      }
    });
  }

  stateChangeHandler = changes => {
    let {
      selectedItem = this.state.selectedItem,
      isOpen = this.state.isOpen,
      inputValue = this.state.inputValue,
      type,
    } = changes;
    isOpen = type === Downshift.stateChangeTypes.mouseUp ? this.state.isOpen : isOpen;
    this.setState({
      selectedItem: selectedItem,
      isOpen,
      inputValue,
    }, () => {
      if (changes.selectedItem && this.props.onChange) {
        this.props.onChange({
          target: {
            value: selectedItem,
          },
        });
      }
    });
  }

  render() {
    const {classes, onChange, ...otherProps} = this.props;
    return (
      <Downshift
        selectedItem={this.state.selectedItem}
  //      itemToString={(item) => item ? item.id : ''}
        isOpen={this.state.isOpen}
        inputValue={this.state.inputValue}
        onChange={this.changeHandler}
        onStateChange={this.stateChangeHandler}
      >
        {({ getInputProps, getItemProps, isOpen, inputValue, selectedItem, highlightedIndex }) => (
          <div className={classes.root}>
            {renderInput({
              fullWidth: true,
              classes,
              InputProps: getInputProps({
                placeholder: 'Search a gene...',
                id: 'gene-search-box',
                onChange: (event) => this.handleInputChange(event),
                onKeyDown: event => {
                  if (event.key === 'Enter' && (highlightedIndex || highlightedIndex ===0)) {
                    const highlightedSuggestion = this.state.suggestions[highlightedIndex];
                    if (highlightedSuggestion) {
                      console.log(`/gene/id/${highlightedSuggestion.id}`);
                    } else {
                      console.log(`/gene/id/${inputValue}`);
                    }
                  }
                  //InputProps.onKeyDown && InputProps.onKeyDown(event);
                  // if (event.key === 'Enter') {
                  //   // Prevent Downshift's default 'Enter' behavior.
                  //    // event.preventDownshiftDefault = true;
                  //   history.push(`/gene/id/${InputProps.value}`);
                  // }
                },
              }),
              ...otherProps,
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
