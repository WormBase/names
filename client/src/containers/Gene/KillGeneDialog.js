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

class KillGeneDialog extends Component {
  submitData = (data) => {
    return mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        if (data.reason) {
          return mockFetch.delete('*', {
          });
        } else {
          return mockFetch.delete('*', {
            body: {
              error: 'Reason for killing a gene is required',
            },
            status: 400,
          })
        }
      },
      () => {
        return this.props.authorizedFetch(`/api/gene/${this.props.wbId}`, {
          method: 'DELETE',
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
        title="Kill gene"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Kill {geneName}</ProgressButton>
        )}
        {...otherProps}>
        {
          ({withFieldData, errorMessage}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <DialogContent>
                <DialogContentText>
                  Gene <strong>{geneName}</strong> will be killed. Are you sure?
                </DialogContentText>
                <DialogContentText>
                  <ValidationError {...errorMessage} />
                </DialogContentText>
                <ReasonField
                  label="Reason"
                  helperText="Enter the reason for killing the gene"
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

KillGeneDialog.propTypes = {
  geneName: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  authorizedFetch: PropTypes.func.isRequired,
};

const styles = (theme) => ({
  submitButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(KillGeneDialog);
