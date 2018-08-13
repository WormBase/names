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

class UndoMergeGeneDialog extends Component {

  submitData = (data) => {
    return mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.delete('*', {
          'live': 'WB1',
          'dead': 'WB2',
        });
      },
      () => {
        return this.props.authorizedFetch(`/api/gene/${this.props.wbIdMergeInto}/split/${this.props.wbId}`, {
          method: 'DELETE',
        });
      },
    );
  }

  render() {
    const {wbId, wbIdMergeInto, geneName, geneNameMergeInto, authorizedFetch, ...otherProps} = this.props;
    return (
      <AjaxDialog
        title="Undo gene merge"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Split {geneNameMergeInto}</ProgressButton>
        )}
        {...otherProps}>
        {
          ({withFieldData, errorMessage}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <DialogContent>
                <DialogContentText>
                  Gene <strong>{geneName}</strong> will be split from <strong>{geneNameMergeInto}</strong>.
                  Are you sure?
                </DialogContentText>
                <DialogContentText>
                  <Typography color="error">{errorMessage}</Typography>
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

UndoMergeGeneDialog.propTypes = {
  wbId: PropTypes.string.isRequired,
  wbIdMergeInto: PropTypes.string.isRequired,
  geneName: PropTypes.string.isRequired,
  geneNameMergeInto: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

const styles = (theme) => ({
  UndoMergeButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(UndoMergeGeneDialog);
