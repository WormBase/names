import React from 'react';
import { TextField as MuiTextField } from 'material-ui';

const TextField = (props) => {
  const {data, required, helperText, ...others} = props;
  const {id, error, value} = data;
  return (
    <MuiTextField
      {...others}
      id={id}
      error={Boolean(error)}  //use Boolean as a function
      helperText={
        <span>
          {required ? 'Required - ' : null}
          {helperText}
          <br/>
          {error}
        </span>
      }
    />
  );
};

export default TextField;
