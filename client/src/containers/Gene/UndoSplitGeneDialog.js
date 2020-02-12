import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import {
  withStyles,
  AjaxDialog,
  DialogContent,
  DialogContentText,
  ProgressButton,
  TextArea,
  ValidationError,
} from '../../components/elements';
import { createOpenOnlyTypeChecker } from '../../utils/types';

class UndoSplitGeneDialog extends Component {
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
          `${this.props.apiPrefix}/${this.props.wbId}/split/${
            this.props.wbIntoId
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
      wbIntoId,
      geneName,
      geneIntoName,
      ...otherProps
    } = this.props;
    return (
      <AjaxDialog
        title="Undo gene split"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Merge into {geneName}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextArea, 'why');
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{geneIntoName}</strong> will be merged into{' '}
                <strong>{geneName}</strong>. Are you sure?
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
          );
        }}
      </AjaxDialog>
    );
  }
}

UndoSplitGeneDialog.propTypes = {
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  wbIntoId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  geneName: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  geneIntoName: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  apiPrefix: PropTypes.string.isRequired,
};

const styles = (theme) => ({});

export default withStyles(styles)(UndoSplitGeneDialog);
