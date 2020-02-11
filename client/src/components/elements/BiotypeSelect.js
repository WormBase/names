import React from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core';
import TextField from './TextField';

const BiotypeSelect = (props) => {
  const BIOTYPES = [
    {
      id: 'cds',
      label: 'CDS',
      sequenceOntology: 'SO:0000316',
    },
    {
      id: 'pseudogene',
      label: 'Pseudogene',
      sequenceOntology: 'SO:0000336',
    },
    {
      id: 'transcript',
      label: 'Transcript',
      sequenceOntology: 'SO:0000673',
    },
    {
      id: 'transposable-element-gene',
      label: 'Transposable element gene',
      sequenceOntology: 'SO:0000111',
    },
  ];

  const biotypeOptions =
    props.required && props.value
      ? [...BIOTYPES]
      : [
          {
            id: null,
            label: '',
            sequenceOntology: '',
          },
          ...BIOTYPES,
        ];
  const { classes, ...others } = props;
  return (
    <TextField
      select
      label="Biotype"
      InputProps={{ className: classes.inputRoot }}
      SelectProps={{ native: true }}
      {...others}
    >
      {biotypeOptions.map((biotype) => (
        <option key={biotype.id} value={biotype.id}>
          {biotype.label
            ? `${biotype.label} [${biotype.sequenceOntology}]`
            : null}
        </option>
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
