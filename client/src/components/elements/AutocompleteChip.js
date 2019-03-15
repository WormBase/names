import React from 'react';
import PropTypes from 'prop-types';
import Chip from '@material-ui/core/Chip';

export default function AutocompleteChip(props) {
  const { suggestion = {}, ...others } = props;
  const { entityType } = suggestion;
  let name;
  switch (entityType) {
    case 'gene':
      name = suggestion['cgc-name'] || suggestion['sequence-name'];
      break;
    default:
  }
  const label = `${name} [${suggestion.id}]`;
  return <Chip tabIndex={-1} label={label} {...others} />;
}
