import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';
import { mockFetchOrNot } from '../../mock';
import { useDataFetch } from '../../containers/Authenticate';

const SpeciesSelect = (props) => {
  const memoizedFetchFunc = useCallback(
    (authorizedFetch) =>
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', [
            {
              'cgc-name-pattern': '^[a-z]{3,4}-[1-9]\\d*(\\.\\d+)?$',
              id: 'species/c-elegans',
              'latin-name': 'Caenorhabditis elegans',
              'sequence-name-pattern':
                '^[A-Z0-9_cel]+\\.[1-9]\\d{0,3}[A-Za-z]?$',
            },
            {
              'cgc-name-pattern': '^Cbr-[a-z21]{3,4}-[1-9]\\d*(\\.\\d+)?$',
              id: 'species/c-briggsae',
              'latin-name': 'Caenorhabditis briggsae',
              'sequence-name-pattern': '^CBG\\d{5}$',
            },
          ]);
        },
        () =>
          authorizedFetch(`/api/species`, {
            method: 'GET',
          })
      ),
    []
  );

  const { data } = useDataFetch(memoizedFetchFunc, []);
  const SPECIES = [
    'Caenorhabditis elegans',
    ...data
      .map((species) => species['latin-name'])
      .filter((speciesName) => speciesName !== 'Caenorhabditis elegans')
      .sort(),
  ];
  const speciesOptions = props.required ? [...SPECIES] : [null, ...SPECIES];
  const { classes, ...others } = props;

  return (
    <TextField
      select
      label="Species"
      InputProps={{ className: classes.inputRoot }}
      {...others}
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
