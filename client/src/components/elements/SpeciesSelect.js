import React from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';

const SpeciesSelect = (props) => {
  const SPECIES = [
    "Caenorhabditis elegans",
    "Caenorhabditis briggsae",
    "Caenorhabditis remanei",
    "Caenorhabditis brenneri",
    "Pristionchus pacificus",
    "Caenorhabditis japonica",
    "Brugia malayi",
    "Onchocerca volvulus",
    "Strongyloides ratti",
  ];
  const speciesOptions = props.required ? [...SPECIES] : [null, ...SPECIES];

  return (
    <TextField
      select
      label="Species"
      className={props.classes.root}
      {...props}
    >
      {speciesOptions.map(species => (
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
  root: {
    minWidth: 100,
  },
});

export default withStyles(styles)(SpeciesSelect);
