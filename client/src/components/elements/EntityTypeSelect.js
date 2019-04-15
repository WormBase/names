import React from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';
import { ENTITY_TYPES } from '../../utils/entityTypes';

const EntityTypeSelect = (props) => {
  return (
    <TextField select className={props.classes.root} {...props}>
      {ENTITY_TYPES.map((item) => (
        <MenuItem key={item} value={item}>
          {item}
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
