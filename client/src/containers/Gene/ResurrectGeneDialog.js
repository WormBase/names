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
  Typography,
} from '../../components/elements';

class ResurrectGeneDialog extends Component {

  submitData = (data) => {
    return mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.post('*', {
          "updated": {
            "gene/id": this.props.wbId,
          }
        });
      },
      () => {
        return this.props.authorizedFetch(`/api/gene/${this.props.wbId}/resurrect`, {
          method: 'POST',
        });
      },
    );
  }

  render() {
    const {wbId, geneName, authorizedFetch, ...otherProps} = this.props;
    return (
      <AjaxDialog
        title="Resurrect gene"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Resurrect {geneName}</ProgressButton>
        )}
        {...otherProps}>
        {
          ({withFieldData, errorMessage}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <DialogContent>
                <DialogContentText>
                  Gene <strong>{geneName}</strong> will be resurrected. Are you sure?
                </DialogContentText>
                <DialogContentText>
                  <Typography color="error">{errorMessage}</Typography>
                </DialogContentText>
                <ReasonField
                  label="Reason"
                  helperText="Enter the reason for resurrecting the gene"
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

ResurrectGeneDialog.propTypes = {
  geneName: PropTypes.string.isRequired,
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

const styles = (theme) => ({
  ResurrectButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(ResurrectGeneDialog);
