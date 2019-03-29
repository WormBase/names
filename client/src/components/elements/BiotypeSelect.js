import React from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';

const BiotypeSelect = (props) => {
  const BIOTYPES = [
    {
      id: 'biotype/cds',
      label: 'CDS',
      sequenceOntology: 'SO:0000316',
    },
    {
      id: 'biotype/pseudogene',
      label: 'Psuedogene',
      sequenceOntology: 'SO:0000336',
    },
    {
      id: 'biotype/transcript',
      label: 'Transcript',
      sequenceOntology: 'SO:0000673',
    },
    {
      id: 'biotype/transposable-element-gene',
      label: 'Transposable element gene',
      sequenceOntology: 'SO:0000111',
    },
  ];

  const biotypeOptions = props.required
    ? [...BIOTYPES]
    : [
        {
          id: null,
          label: '',
          sequenceOntology: '',
        },
        ...BIOTYPES,
      ];
  const { classes } = props;
  return (
    <TextField
      select
      label="Biotype"
      InputProps={{ classes: { root: classes.inputRoot } }}
      {...props}
    >
      {biotypeOptions.map((biotype) => (
        <MenuItem key={biotype.id} value={biotype.id}>
          {biotype.label
            ? `${biotype.label} [${biotype.sequenceOntology}]`
            : null}
        </MenuItem>
      ))}
    </TextField>
  );
};

BiotypeSelect.propTypes = {
  classes: PropTypes.object.isRequired,
  required: PropTypes.bool,
};

const styles = (theme) => ({
  inputRoot: {
    minWidth: 100,
  },
});

export default withStyles(styles)(BiotypeSelect);
