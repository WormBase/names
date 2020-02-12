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
  ValidationError,
} from '../../components/elements';
import { createOpenOnlyTypeChecker } from '../../utils/types';

class EntityDialogKill extends Component {
  submitData = (data, authorizedFetch) => {
    const { wbId, apiPrefix } = this.props;
    return mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        if (data.reason) {
          return mockFetch.delete('*', {});
        } else {
          return mockFetch.delete('*', {
            body: {
              error: 'Reason for killing a gene is required',
            },
            status: 400,
          });
        }
      },
      () => {
        return authorizedFetch(`${apiPrefix}/${wbId}`, {
          method: 'DELETE',
          body: JSON.stringify({
            ...data,
          }),
        });
      }
    );
  };

  render() {
    const { wbId, name, entityType, ...otherProps } = this.props;
    return (
      <AjaxDialog
        title={`Kill ${entityType} ${name}`}
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Kill {name}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextArea, 'why');
          return (
            <DialogContent>
              <DialogContentText>
                {capitalize(entityType)} <strong>{name}</strong> will be killed.
                Are you sure?
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <ReasonField
                label="Reason"
                helperText={`Enter the reason for killing this ${entityType}`}
                fullWidth
              />
            </DialogContent>
          );
        }}
      </AjaxDialog>
    );
  }
}

EntityDialogKill.propTypes = {
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

export default withStyles(styles)(EntityDialogKill);
