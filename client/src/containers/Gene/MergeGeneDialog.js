import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import {
  withStyles,
  BaseForm,
  BiotypeSelect,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  ProgressButton,
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
  TextField,
  Typography,
} from '../../components/elements';
import GeneAutocomplete from './GeneAutocomplete';

class MergeGeneDialog extends Component {
  constructor(props) {
    super(props);
    this.state = {
      errorMessage: null,
      status: null,
    };
  }

  handleSubmit = ({geneIdMergeInto, ...data}) => {
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          console.log(data);
          if (data['provenance/why']) {
            return mockFetch.post('*', {
            });
          } else {
            return mockFetch.post('*', {
              body: {
                message: 'Reason for merging a gene is required',
              },
              status: 400,
            })
          }
        },
        () => {
          return this.props.authorizedFetch(`/api/gene/${geneIdMergeInto}/merge/${this.props.wbId}`, {
            method: 'POST',
            body: JSON.stringify(data),
          });
        },
      ).then((response) => response.json()).then((response) => {
        if (!response.problems) {
          this.setState({
            errorMessage: null,
            status: 'COMPLETE',
          })
          this.props.onSubmitSuccess && this.props.onSubmitSuccess({});
        } else {
          this.setState({
            errorMessage: JSON.stringify(response),
            status: 'COMPLETE',
          });
          this.props.onSubmitError && this.props.onSubmitError(response);
        }
      }).catch((e) => console.log('error', e));
    });
  }

  render() {
    return (
      <BaseForm>
        {
          ({withFieldData, getFormData, resetData}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            const GeneIdMergeIntoField = withFieldData(GeneAutocomplete, 'geneIdMergeInto');
            const BiotypeField = withFieldData(BiotypeSelect, 'gene/biotype');
            return (
              <Dialog
                open={this.props.open}
                onClose={this.props.onClose}
                aria-labelledby="form-dialog-title"
              >
                <DialogTitle id="form-dialog-title">Merge gene</DialogTitle>
                <DialogContent>
                  <DialogContentText>
                    Gene <strong>{this.props.geneName}</strong> will be merged. Are you sure?
                  </DialogContentText>
                  <DialogContentText>
                    <Typography color="error">{this.state.errorMessage}</Typography>
                  </DialogContentText>
                <GeneIdMergeIntoField
                  label="Merge into gene"
                  helperText="Enter WBID or search by CGC name"
                  required
                />
                <BiotypeField
                  helperText={`Set the biotype of the merged gene`}
                  required
                  classes={{
                    root: this.props.classes.biotypeSelectField,
                  }}
                />
                <ReasonField
                  label="Reason"
                  helperText="Enter the reason for merging the gene"
                  required
                  fullWidth
                />
                </DialogContent>
                <DialogActions>
                  <Button
                    onClick={this.props.onClose}
                    color="primary"
                  >
                    Cancel
                  </Button>
                  <ProgressButton
                    onClick={() => this.handleSubmit(getFormData())}
                    status={this.state.status === 'SUBMITTED' ? PROGRESS_BUTTON_PENDING : PROGRESS_BUTTON_READY}
                    className={this.props.classes.mergeButton}
                  >
                    Merge and kill {this.props.geneName}
                  </ProgressButton>
                </DialogActions>
              </Dialog>
            )
          }
        }
      </BaseForm>
    );
  }
}

MergeGeneDialog.propTypes = {
  wbId: PropTypes.string.isRequired,
  geneName: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmitSuccess: PropTypes.func,
  onSubmitError: PropTypes.func,
};

const styles = (theme) => ({
  mergeButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(MergeGeneDialog);
