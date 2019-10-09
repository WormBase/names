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

class EntityDialogResurrect extends Component {
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
          `${this.props.apiPrefix}/${this.props.wbId}/resurrect`,
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
    const { wbId, entityType, name, ...otherProps } = this.props;
    return (
      <AjaxDialog
        title={`Resurrect ${entityType} ${name}`}
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Resurrect {name}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextField, 'provenance/why');
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{name}</strong> will be resurrected. Are you sure?
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <ReasonField
                label="Reason"
                helperText="Enter the reason for resurrecting the gene"
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

EntityDialogResurrect.propTypes = {
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

export default withStyles(styles)(EntityDialogResurrect);
