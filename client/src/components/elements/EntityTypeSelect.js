import React, { useContext } from 'react';
import PropTypes from 'prop-types';
import { MenuItem, withStyles } from '@material-ui/core';
import TextField from './TextField';
import { EntityTypesContext } from '../../containers/Entity';

const EntityTypeSelect = (props) => {
  const entityTypesMap = useContext(EntityTypesContext);
  return (
    <TextField select className={props.classes.root} {...props}>
      {[...entityTypesMap].map(([, { entityType: item, displayName }]) => (
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
