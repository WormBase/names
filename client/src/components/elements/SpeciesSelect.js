import React from 'react';
import PropTypes from 'prop-types';
import { TextField, MenuItem, withStyles } from 'material-ui';

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
  return (
    <TextField
      select
      label="Species"
      className={props.classes.root}
      {...props}
    >
      {[null, ...SPECIES,].map(species => (
        <MenuItem key={species} value={species}>
          {species}
        </MenuItem>
      ))}
    </TextField>
  );
};

SpeciesSelect.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    minWidth: 100,
  },
});

export default withStyles(styles)(SpeciesSelect);
