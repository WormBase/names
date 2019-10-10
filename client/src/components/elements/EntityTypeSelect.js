import React from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';
import { useEntityTypes } from '../../containers/Entity';

const EntityTypeSelect = (props) => {
  const { entityTypesAll } = useEntityTypes();
  return (
    <TextField select className={props.classes.root} {...props}>
      {entityTypesAll.map(({ entityType: item, displayName }) => (
        <MenuItem key={item} value={item}>
          {displayName}
        </MenuItem>
      ))}
    </TextField>
  );
};

EntityTypeSelect.propTypes = {
  classes: PropTypes.object.isRequired,
  required: PropTypes.bool,
};

const styles = (theme) => ({
  root: {},
});

export default withStyles(styles)(EntityTypeSelect);
