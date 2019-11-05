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

class UndoMergeGeneDialog extends Component {
  submitData = (data, authorizedFetch) => {
    return mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.delete('*', {
          live: 'WB1',
          dead: 'WB2',
        });
      },
      () => {
        return authorizedFetch(
          `${this.props.apiPrefix}/${this.props.wbId}/merge/${
            this.props.wbFromId
          }`,
          {
            method: 'DELETE',
          }
        );
      }
    );
  };

  render() {
    const {
      wbId,
      wbFromId,
      geneName,
      geneFromName,
      ...otherProps
    } = this.props;
    return (
      <AjaxDialog
        title="Undo gene merge"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Split {geneName}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextField, 'why');
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{geneFromName}</strong> will be split from{' '}
                <strong>{geneName}</strong>. Are you sure?
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <ReasonField
                label="Reason"
                helperText="Enter the reason for undoing the merge"
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

UndoMergeGeneDialog.propTypes = {
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  wbFromId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  geneName: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  geneFromName: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  apiPrefix: PropTypes.string.isRequired,
};

const styles = (theme) => ({});

export default withStyles(styles)(UndoMergeGeneDialog);
