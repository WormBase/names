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
import GeneAutocomplete from './GeneAutocomplete';

class MergeGeneDialog extends Component {
  submitData = (
    { data: rawData = {}, prov: provenance } = {},
    authorizedFetch
  ) => {
    const { geneIdMergeInto, ...data } = rawData;
    return mockFetchOrNot(
      (mockFetch) => {
        if (provenance['provenance/why']) {
          return mockFetch.post('*', {});
        } else {
          return mockFetch.post('*', {
            body: {
              message: 'Reason for merging a gene is required',
            },
            status: 400,
          });
        }
      },
      () => {
        return authorizedFetch(
          `/api/gene/${geneIdMergeInto}/merge/${this.props.wbId}`,
          {
            method: 'POST',
            body: JSON.stringify({
              data: data,
              prov: provenance,
            }),
          }
        );
      }
    );
  };

  render() {
    const { wbId, geneName, ...otherProps } = this.props;
    return (
      <AjaxDialog
        title="Merge gene"
        submitter={this.submitData}
        renderSubmitButton={(props) => (
          <ProgressButton {...props}>Merge and kill {geneName}</ProgressButton>
        )}
        {...otherProps}
      >
        {({ withFieldData, errorMessage }) => {
          const ReasonField = withFieldData(TextField, 'provenance/why');
          const GeneIdMergeIntoField = withFieldData(
            GeneAutocomplete,
            'geneIdMergeInto'
          );
          const BiotypeField = withFieldData(BiotypeSelect, 'gene/biotype');
          return (
            <DialogContent>
              <DialogContentText>
                Gene <strong>{geneName}</strong> will be merged into the gene
                you specify below. Are you sure?
              </DialogContentText>
              <DialogContentText>
                <ValidationError {...errorMessage} />
              </DialogContentText>
              <GeneIdMergeIntoField
                label="Merge into gene"
                helperText={
                  <span>
                    Enter WBID or CGC name of the gene that{' '}
                    <strong>{geneName}</strong> will be merged into
                  </span>
                }
                required
                pageSize={3}
              />
              <BiotypeField
                helperText={`Set the biotype of the merged gene`}
                required
              />
              <ReasonField
                label="Reason"
                helperText="Enter the reason for merging the gene"
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

MergeGeneDialog.propTypes = {
  wbId: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
  geneName: createOpenOnlyTypeChecker(PropTypes.string.isRequired),
};

const styles = (theme) => ({});

export default withStyles(styles)(MergeGeneDialog);
