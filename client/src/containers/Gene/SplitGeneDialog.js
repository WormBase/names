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
  TextField,
  Typography,
} from '../../components/elements';

class SplitGeneDialog extends Component {
  constructor(props) {
    super(props);
    this.state = {
      errorMessage: null,
    };
  }

  handleSubmit = (data) => {
    mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        const emptyFields = ['provenance/why', 'gene/biotype', 'product'].filter(
          (fieldId) => {
            return !data[fieldId];
          }
        );
        let errorMessage;
        switch (emptyFields.length) {
          case 0:
            break;
          case 1:
            errorMessage = `Field ${emptyFields[0]} needs to be filled in.`
            break;
          default:
            errorMessage = `Fields ${emptyFields.slice(0, -1).join(`, `)} and ${emptyFields.slice(-1)[0]} need to be filled in.`
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
            "updated": {
              "gene/id": this.props.wbId,
            },
            "created": {
              "gene/id": "WB3",
              "gene/status": 'gene.status/live',
            },
          });
        }
      },
      () => {
        return fetch(`/api/gene/${this.props.wbId}/split`, {
          method: 'POST',
          body: JSON.stringify(data),
        });
      },
    ).then((response) => response.json()).then((response) => {
      if (!response.problems) {
        this.props.onSubmitSuccess && this.props.onSubmitSuccess({...response.updated});
      } else {
        this.setState({
          errorMessage: JSON.stringify(response),
        });
        this.props.onSubmitError && this.props.onSubmitError(response);
      }
    }).catch((e) => console.log('error', e));
  }

  render() {
    return (
      <BaseForm data={{'gene/biotype': this.props.biotypeOriginal}}>
        {
          ({withFieldData, getFormData, resetData}) => {
            const BiotypeSelectOriginalField = withFieldData(BiotypeSelect, 'gene/biotype');
            const ReasonField = withFieldData(TextField, 'provenance/why');
            const SequenceNameField = withFieldData(TextField, 'product:gene/sequence-name');
            const BiotypeSelectField = withFieldData(BiotypeSelect, 'product:gene/biotype');
            return (
              <Dialog
                open={this.props.open}
                onClose={this.props.onClose}
                aria-labelledby="form-dialog-title"
              >
                <DialogTitle id="form-dialog-title">Split gene</DialogTitle>
                <DialogContent>
                  <DialogContentText>
                    Gene <strong>{this.props.geneName}</strong> will be split.
                  </DialogContentText>
                  <DialogContentText>
                    <Typography color="error">{this.state.errorMessage}</Typography>
                  </DialogContentText>
                  <ReasonField
                    label="Reason"
                    helperText="Enter the reason for splitting the gene"
                    required
                    fullWidth
                  />
                  <BiotypeSelectOriginalField
                    label={`Modify the biotype of ${this.props.geneName}`}
                    classes={{
                      root: this.props.classes.biotypeSelectField,
                    }}
                  />
                  <br />
                  <DialogContentText>
                    Please provide information of the gene to be created.
                  </DialogContentText>
                  <SequenceNameField
                    label="Sequence name"
                  />
                  <BiotypeSelectField />
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
                    className={this.props.classes.splitButton}
                  >
                    split {this.props.geneName}
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

SplitGeneDialog.propTypes = {
  wbId: PropTypes.string.isRequired,
  geneName: PropTypes.string.isRequired,
  biotypeOriginal: PropTypes.string.isRequired,
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmitSuccess: PropTypes.func,
  onSubmitError: PropTypes.func,
};

const styles = (theme) => ({
  splitButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
  biotypeSelectField: {
    minWidth: 200,
  }
});

export default withStyles(styles)(SplitGeneDialog);
