import React from 'react';
import PropTypes from 'prop-types';
import { TextField, MenuItem, withStyles } from 'material-ui';

const BiotypeSelect = (props) => {
  const BIOTYPES = [
    {
      id: null,
      label: '',
    },
    {
      id: 'cds',
      label: 'CDS',
    },
    {
      id: 'psuedogene',
      label: 'Psuedogene',
    },
    {
      id: 'transcript',
      label: 'Transcript',
    },
    {
      id: 'transposon',
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
