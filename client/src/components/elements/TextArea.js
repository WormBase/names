import React from 'react';
import TextField from './TextField';

export default function TextArea(props) {
  return <TextField multiline fullWidth minRows={4} {...props} />;
}
