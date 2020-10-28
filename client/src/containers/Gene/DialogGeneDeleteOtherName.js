import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import { capitalize } from '../../utils/format';
import PropTypes from 'prop-types';
import {
  withStyles,
  AjaxDialog,
  DialogContent,
  DialogContentText,
  ProgressButton,
  TextArea,
  TextField,
  ListField,
  ValidationError,
} from '../../components/elements';
import { createOpenOnlyTypeChecker } from '../../utils/types';

class DialogGeneDeleteOtherName extends Component {
  submitData = (data, authorizedFetch) => {
    return mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.delete('*', {
          updated: {
            id: this.props.wbId,
          },
        });
      },
      () => {
        return authorizedFetch(
          `${this.props.apiPrefix}/${this.props.wbId}/update-other-names`,
          {
            method: 'DELETE',
            body: JSON.stringify({
              ...data,
            }),
          }
        );
      }
    );
  };

  render() {
    const {
      wbId,
      name,
      entityType,
      data: entityData,
      operationPayload,
      ...otherProps
    } = this.props;
    return (
      <AjaxDialog
        title={`Delete alternative name ${operationPayload.otherName}`}
        data={{
          'other-names': [operationPayload.otherName],
        }}
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Delete name</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextArea, 'why');
          return (
            <DialogContent>
              <DialogContentText>
                Alternative name <strong>{operationPayload.otherName}</strong>{' '}
                will be deleted from {capitalize(entityType)}{' '}
                <strong>
                  {name} ({wbId})
                </strong>
                . Are you sure?
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>

              <ReasonField
                label="Reason"
                helperText={`Enter the reason for deleting the alternative name`}
                fullWidth
              />
            </DialogContent>
          );
        }}
      </AjaxDialog>
    );
  }
}

DialogGeneDeleteOtherName.propTypes = {
  name: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  entityType: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  apiPrefix: PropTypes.string.isRequired,
  operationPayload: PropTypes.shape({
    otherName: PropTypes.string.isRequired,
  }).isRequired,
};

const styles = (theme) => ({
  submitButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(DialogGeneDeleteOtherName);
