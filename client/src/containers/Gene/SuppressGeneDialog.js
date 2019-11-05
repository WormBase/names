import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import {
  withStyles,
  AjaxDialog,
  DialogContent,
  DialogContentText,
  ProgressButton,
  TextField,
  ValidationError,
} from '../../components/elements';
import { createOpenOnlyTypeChecker } from '../../utils/types';

class SuppressGeneDialog extends Component {
  submitData = (data, authorizedFetch) => {
    return mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        if (data.reason) {
          return mockFetch.post('*', {});
        } else {
          return mockFetch.post('*', {
            body: {
              error: 'Reason for suppressing a gene is required',
            },
            status: 400,
          });
        }
      },
      () => {
        return authorizedFetch(
          `${this.props.apiPrefix}/${this.props.wbId}/suppress`,
          {
            method: 'POST',
            body: JSON.stringify({
              ...data,
            }),
          }
        );
      }
    );
  };

  render() {
    const { wbId, name, ...otherProps } = this.props;
    return (
      <AjaxDialog
        title="Suppress gene"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Suppress {name}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextField, 'why');
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{name}</strong> will be suppressed. Are you sure?
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <ReasonField
                label="Reason"
                helperText="Enter the reason for suppressing the gene"
                required
                fullWidth
              />
            </DialogContent>
          );
        }}
      </AjaxDialog>
    );
  }
}

SuppressGeneDialog.propTypes = {
  name: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  apiPrefix: PropTypes.string.isRequired,
};

const styles = (theme) => ({
  submitButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(SuppressGeneDialog);
