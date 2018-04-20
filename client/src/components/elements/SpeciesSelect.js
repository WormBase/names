import React from 'react';
import { TextField, MenuItem } from 'material-ui';

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

export default SpeciesSelect;
