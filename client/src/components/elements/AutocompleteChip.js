import React from 'react';
import PropTypes from 'prop-types';
import Chip from '@material-ui/core/Chip';
import { withStyles } from '@material-ui/core/styles';

function AutocompleteChip(props) {
  const { classes = {}, suggestion = {}, ...others } = props;
  const { entityType } = suggestion;
  let name;
  switch (entityType) {
    case 'gene':
      name = suggestion['cgc-name'] || suggestion['sequence-name'];
      break;
    default:
      name = suggestion.name;
  }
  const label = (
    <span>
      {name}
      <span className={classes.wbId}>[{suggestion.id}]</span>
    </span>
  );
  return <Chip tabIndex={-1} label={label} {...others} />;
}

AutocompleteChip.propTypes = {
  classes: PropTypes.object.isRequired,
  suggestion: PropTypes.shape({
    id: PropTypes.string.isRequired,
    entityType: PropTypes.string.isRequired,
  }),
};

const styles = (theme) => ({
  wbId: {
    fontSize: '0.8em',
    display: 'inline-block',
    padding: `0 ${theme.spacing.unit / 2}px`,
  },
});

export default withStyles(styles)(AutocompleteChip);
