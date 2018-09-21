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

class SuppressGeneDialog extends Component {
  submitData = (data) => {
    return mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        if (data.reason) {
          return mockFetch.post('*', {
          });
        } else {
          return mockFetch.post('*', {
            body: {
              error: 'Reason for suppressing a gene is required',
            },
            status: 400,
          })
        }
      },
      () => {
        return this.props.authorizedFetch(`/api/gene/${this.props.wbId}/suppress`, {
          method: 'POST',
          body: JSON.stringify({
            ...data
          })
        });
      },
    );
  }

  render() {
    const {wbId, geneName, authorizedFetch, ...otherProps} = this.props;
    return (
      <AjaxDialog
        title="Suppress gene"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Suppress {geneName}</ProgressButton>
        )}
        {...otherProps}>
        {
          ({withFieldData, errorMessage}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <DialogContent>
                <DialogContentText>
                  Gene <strong>{geneName}</strong> will be suppressed. Are you sure?
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
            )
          }
        }
      </AjaxDialog>
    );
  }
}

SuppressGeneDialog.propTypes = {
  geneName: PropTypes.string.isRequired,
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

const styles = (theme) => ({
  suppressButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(SuppressGeneDialog);
