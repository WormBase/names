import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import {
  withStyles,
  AjaxDialog,
  BiotypeSelect,
  DialogContent,
  DialogContentText,
  ProgressButton,
  TextArea,
  TextField,
  ValidationError,
} from '../../components/elements';
import { createOpenOnlyTypeChecker } from '../../utils/types';

class SplitGeneDialog extends Component {
  submitData = (data, authorizedFetch) => {
    return mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        const emptyFields = ['why', 'biotype', 'product'].filter((fieldId) => {
          return !data[fieldId];
        });
        let errorMessage;
        switch (emptyFields.length) {
          case 0:
            break;
          case 1:
            errorMessage = `Field ${emptyFields[0]} needs to be filled in.`;
            break;
          default:
            errorMessage = `Fields ${emptyFields.slice(0, -1).join(`, `)} and ${
              emptyFields.slice(-1)[0]
            } need to be filled in.`;
        }

        if (errorMessage) {
          return mockFetch.post('*', {
            body: {
              errors: errorMessage,
            },
            status: 400,
          });
        } else {
          return mockFetch.post('*', {
            updated: {
              id: this.props.wbId,
            },
            created: {
              id: 'WB3',
              status: 'live',
            },
          });
        }
      },
      () => {
        return authorizedFetch(
          `${this.props.apiPrefix}/${this.props.wbId}/split`,
          {
            method: 'POST',
            body: JSON.stringify(data),
          }
        );
      }
    );
  };

  render() {
    const { classes, name, wbId, data = {}, ...otherProps } = this.props;
    return (
      <AjaxDialog
        title="Split gene"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Split {name}</ProgressButton>
        )}
        {...otherProps}
        data={{
          ...data,
          'product:biotype': data.biotype,
        }}
      >
        {({ withFieldData, errorMessage }) => {
          const BiotypeSelectOriginalField = withFieldData(
            BiotypeSelect,
            'biotype'
          );
          const ReasonField = withFieldData(TextArea, 'why');
          const SequenceNameField = withFieldData(
            TextField,
            'product:sequence-name'
          );
          const BiotypeSelectField = withFieldData(
            BiotypeSelect,
            'product:biotype'
          );
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{name}</strong> will be split.
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <BiotypeSelectOriginalField
                label={`${name || wbId} biotype`}
                helperText={`Modify the biotype of ${name}`}
                required
                InputProps={{
                  className: classes.biotypeSelectFieldInput,
                }}
              />
              <br />
              <DialogContentText>
                Please provide information of the gene to be created.
              </DialogContentText>
              <SequenceNameField label="Sequence name" />
              <BiotypeSelectField required />
              <ReasonField
                label="Reason"
                helperText="Enter the reason for splitting the gene"
                fullWidth
              />
            </DialogContent>
          );
        }}
      </AjaxDialog>
    );
  }
}

SplitGeneDialog.propTypes = {
  classes: PropTypes.object.isRequired,
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  name: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  apiPrefix: PropTypes.string.isRequired,
};

const styles = (theme) => ({
  biotypeSelectFieldInput: {
    minWidth: '15em',
  },
});

export default withStyles(styles)(SplitGeneDialog);
