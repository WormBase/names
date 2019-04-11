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
  TextField,
  ValidationError,
} from '../../components/elements';
import { createOpenOnlyTypeChecker } from '../../utils/types';

class SplitGeneDialog extends Component {
  submitData = (data, authorizedFetch) => {
    return mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        const emptyFields = [
          'provenance/why',
          'gene/biotype',
          'product',
        ].filter((fieldId) => {
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
              'gene/id': this.props.wbId,
            },
            created: {
              'gene/id': 'WB3',
              'gene/status': 'gene.status/live',
            },
          });
        }
      },
      () => {
        return authorizedFetch(`/api/gene/${this.props.wbId}/split`, {
          method: 'POST',
          body: JSON.stringify(data),
        });
      }
    );
  };

  render() {
    const { classes, name, wbId, biotypeOriginal, ...otherProps } = this.props;
    return (
      <AjaxDialog
        title="Split gene"
        data={{ 'gene/biotype': biotypeOriginal }}
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Split {name}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const BiotypeSelectOriginalField = withFieldData(
            BiotypeSelect,
            'gene/biotype'
          );
          const ReasonField = withFieldData(TextField, 'provenance/why');
          const SequenceNameField = withFieldData(
            TextField,
            'product:gene/sequence-name'
          );
          const BiotypeSelectField = withFieldData(
            BiotypeSelect,
            'product:gene/biotype'
          );
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{name}</strong> will be split.
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <ReasonField
                label="Reason"
                helperText="Enter the reason for splitting the gene"
                required
                fullWidth
              />
              <BiotypeSelectOriginalField
                label={`${name || wbId} biotype`}
                helperText={`Modify the biotype of ${name}`}
                required
                classes={{
                  inputRoot: classes.biotypeSelectField,
                }}
              />
              <br />
              <DialogContentText>
                Please provide information of the gene to be created.
              </DialogContentText>
              <SequenceNameField label="Sequence name" />
              <BiotypeSelectField required />
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
  biotypeOriginal: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
};

const styles = (theme) => ({
  biotypeSelectField: {
    minWidth: '15em',
  },
});

export default withStyles(styles)(SplitGeneDialog);
