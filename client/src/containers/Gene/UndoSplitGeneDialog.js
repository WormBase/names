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

class UndoSplitGeneDialog extends Component {

  submitData = (data) => {
    return mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.delete('*', {
          'live': 'WB1',
          'dead': 'WB2',
        });
      },
      () => {
        return this.props.authorizedFetch(`/api/gene/${this.props.wbId}/split/${this.props.wbIntoId}`, {
          method: 'DELETE',
        });
      },
    );
  }

  render() {
    const {wbId, wbIntoId, geneName, geneIntoName, authorizedFetch, ...otherProps} = this.props;
    return (
      <AjaxDialog
        title="Undo gene split"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Merge into {geneName}</ProgressButton>
        )}
        {...otherProps}>
        {
          ({withFieldData, errorMessage}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <DialogContent>
                <DialogContentText>
                  Gene <strong>{geneIntoName}</strong> will be merged into <strong>{geneName}</strong>.
                  Are you sure?
                </DialogContentText>
                <DialogContentText>
                  <ValidationError {...errorMessage} />
                </DialogContentText>
                <ReasonField
                  label="Reason"
                  helperText="Enter the reason for undoing the split"
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

UndoSplitGeneDialog.propTypes = {
  wbId: PropTypes.string.isRequired,
  wbIntoId: PropTypes.string.isRequired,
  geneName: PropTypes.string.isRequired,
  geneIntoName: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

const styles = (theme) => ({
  UndoSplitButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(UndoSplitGeneDialog);
