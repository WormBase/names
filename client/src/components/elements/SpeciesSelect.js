import React from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';

const SpeciesSelect = (props) => {
  const SPECIES = [
    'Caenorhabditis elegans',
    'Caenorhabditis briggsae',
    'Caenorhabditis remanei',
    'Caenorhabditis brenneri',
    'Pristionchus pacificus',
    'Caenorhabditis japonica',
    'Brugia malayi',
    'Onchocerca volvulus',
    'Strongyloides ratti',
  ];
  const speciesOptions = props.required ? [...SPECIES] : [null, ...SPECIES];
  const { classes } = props;

  return (
    <TextField
      select
      label="Species"
      InputProps={{ classes: { root: classes.inputRoot } }}
      {...props}
    >
      {speciesOptions.map((species) => (
        <MenuItem key={species} value={species}>
          {species}
        </MenuItem>
      ))}
    </TextField>
  );
};

SpeciesSelect.propTypes = {
  classes: PropTypes.object.isRequired,
  required: PropTypes.bool,
};

const styles = (theme) => ({
  inputRoot: {
    minWidth: 100,
  },
});

export default withStyles(styles)(SpeciesSelect);
