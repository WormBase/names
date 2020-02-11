import React from 'react';
import TextField from './TextField';

export default function TextArea(props) {
  return <TextField multiline fullWidth rows={4} {...props} />;
}
