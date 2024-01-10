import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core';
import TextField from './TextField';
import { mockFetchOrNot } from '../../mock';
import { useDataFetch } from '../../containers/Authenticate';

const SpeciesSelect = (props) => {
  const memoizedFetchFunc = useCallback(
    (fetchFn) =>
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
          fetchFn(`/api/species`, {
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
  const speciesOptions =
    props.required && props.value ? [...SPECIES] : [null, ...SPECIES];
  const { classes, ...others } = props;

  return (
    <TextField
      select
      label="Species"
      InputProps={{ className: classes.inputRoot }}
      SelectProps={{ native: true }}
      {...others}
    >
      {speciesOptions.map((species) => (
        <option key={species} value={species}>
          {species}
        </option>
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
    minWidth: 200,
  },
});

export default withStyles(styles)(SpeciesSelect);
