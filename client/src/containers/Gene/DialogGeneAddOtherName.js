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

class DialogGeneAddOtherName extends Component {
  submitData = (data, authorizedFetch) => {
    return mockFetchOrNot(
      (mockFetch) => {
        return mockFetch.post('*', {
          updated: {
            id: this.props.wbId,
          },
        });
      },
      () => {
        return authorizedFetch(
          `${this.props.apiPrefix}/${this.props.wbId}/update-other-names`,
          {
            method: 'PUT',
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
      ...otherProps
    } = this.props;
    return (
      <AjaxDialog
        title={`Add alternative names to ${entityType} ${name}`}
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Add name(s)</ProgressButton>
        )}
        data={{
          'other-names': [''],
        }}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextArea, 'why');
          const OtherNamesField = withFieldData(ListField, 'other-names');
          return (
            <DialogContent>
              <DialogContentText>
                Please enter the alternative name to be associated with{' '}
                {capitalize(entityType)}{' '}
                <strong>
                  {name} ({wbId})
                </strong>
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <br />
              <OtherNamesField label="Alternative name(s)" />
              <ReasonField
                label="Reason"
                helperText={`Enter the reason for the alternative name`}
                fullWidth
              />
            </DialogContent>
          );
        }}
      </AjaxDialog>
    );
  }
}

DialogGeneAddOtherName.propTypes = {
  name: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  entityType: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  apiPrefix: PropTypes.string.isRequired,
};

const styles = (theme) => ({
  submitButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(DialogGeneAddOtherName);
