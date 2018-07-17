import React from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from 'material-ui';
import TextField from './TextField';

const BiotypeSelect = (props) => {
  const BIOTYPES = [
    {
      id: null,
      label: '',
    },
    {
      id: 'biotype/cds',
      label: 'CDS',
    },
    {
      id: 'biotype/psuedogene',
      label: 'Psuedogene',
    },
    {
      id: 'biotype/transcript',
      label: 'Transcript',
    },
    {
      id: 'biotype/transposon',
      label: 'Transposon',
    },
  ];
  console.log(BIOTYPES);
  return (
    <TextField
      select
      label="Biotype"
      className={props.classes.root}
      {...props}
    >
      {BIOTYPES.map(biotype => (
        <MenuItem key={biotype.id} value={biotype.id}>
          {biotype.label}
        </MenuItem>
      ))}
    </TextField>
  );
};

BiotypeSelect.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    minWidth: 100,
  },
});

export default withStyles(styles)(BiotypeSelect);
