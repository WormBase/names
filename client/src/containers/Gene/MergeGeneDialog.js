import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import {
  withStyles,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  TextField,
  Typography,
} from '../../components/elements';
import BaseForm from './BaseForm';
import GeneSearchBox from './GeneAutocomplete';

class MergeGeneDialog extends Component {
  constructor(props) {
    super(props);
    this.state = {
      errorMessage: null,
    };
  }

  handleSubmit = (data) => {
    mockFetchOrNot(
      (mockFetch) => {
        console.log(data);
        if (data.reason) {
          return mockFetch.delete('*', {
            id: this.props.wbId,
            reason: data.reason,
            dead: true,
          });
        } else {
          return mockFetch.delete('*', {
            body: {
              error: 'Reason for merging a gene is required',
            },
            status: 400,
          })
        }
      },
      () => {
        return fetch(`/api/gene/${this.props.wbId}`, {
          method: 'DELETE'
        });
      },
      true
    ).then((response) => response.json()).then((response) => {
      if (!response.error) {
        this.props.onSubmitSuccess && this.props.onSubmitSuccess({...response});
      } else {
        this.setState({
          errorMessage: response.error,
        });
        this.props.onSubmitError && this.props.onSubmitError(response.error);
      }
    }).catch((e) => console.log('error', e));
  }

  render() {
    return (
      <BaseForm>
        {
          ({withFieldData, getFormData, resetData}) => {
            const ReasonField = withFieldData(TextField, 'reason');
            const GeneIdMergeIntoField = withFieldData(GeneSearchBox, 'geneIdMergeInto');
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
                  <Button
                    onClick={() => this.handleSubmit(getFormData())}
                    className={this.props.classes.mergeButton}
                  >
                    Merge and kill {this.props.geneName}
                  </Button>
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
  geneName: PropTypes.string.isRequired,
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
