import React from 'react';
import PropTypes from 'prop-types';
import IconButton from '@material-ui/core/IconButton';
import AddCircleOutlineIcon from '@material-ui/icons/AddCircleOutline';
import DeleteIcon from '@material-ui/icons/Delete';
import TextField from './TextField';

const ListField = ({ value: values = [''], onChange, ...TextFieldProps }) => {
  const handleAddValue = (value) => {
    if (onChange) {
      onChange({
        target: {
          value: [...values, ''],
        },
      });
    }
  };

  const handleValueChange = (event, index) => {
    if (onChange) {
      const newValues = [...values];
      newValues[index] = event.target.value;
      onChange({
        target: {
          value: newValues,
        },
      });
    }
  };

  const handleValueDelete = (index) => {
    if (onChange) {
      const newValues = [...values.slice(0, index), ...values.slice(index + 1)];
      console.log(newValues);
      onChange({
        target: {
          value: newValues,
        },
      });
    }
  };

  return (
    <React.Fragment>
      {values.map((v, index) => (
        <div>
          <TextField
            variant="outlined"
            {...TextFieldProps}
            onChange={(event) => handleValueChange(event, index)}
            value={v}
          />
          <IconButton
            aria-label="delete-name"
            onClick={() => handleValueDelete(index)}
          >
            <DeleteIcon />
          </IconButton>
        </div>
      ))}
      <IconButton aria-label="add-name" onClick={handleAddValue}>
        <AddCircleOutlineIcon />
      </IconButton>
    </React.Fragment>
  );
};

ListField.propTypes = {
  value: PropTypes.array,
  onChange: PropTypes.func,
};

export default ListField;
