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

class KillGeneDialog extends Component {
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
        if (data.reason) {
          return mockFetch.delete('*', {
          });
        } else {
          return mockFetch.delete('*', {
            body: {
              error: 'Reason for killing a gene is required',
            },
            status: 400,
          })
        }
      },
      () => {
        return this.props.authorizedFetch(`/api/gene/${this.props.wbId}`, {
          method: 'DELETE',
          body: JSON.stringify({
            ...data
          })
        });
      },
    ).then((response) => response.json()).then((response) => {
      if (!response.problems) {
        this.props.onSubmitSuccess && this.props.onSubmitSuccess({});
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
      <BaseForm>
        {
          ({withFieldData, getFormData, resetData}) => {
            const ReasonField = withFieldData(TextField, 'provenance/why');
            return (
              <Dialog
                open={this.props.open}
                onClose={this.props.onClose}
                aria-labelledby="form-dialog-title"
              >
                <DialogTitle id="form-dialog-title">Kill gene</DialogTitle>
                <DialogContent>
                  <DialogContentText>
                    Gene <strong>{this.props.geneName}</strong> will be killed. Are you sure?
                  </DialogContentText>
                  <DialogContentText>
                    <Typography color="error">{this.state.errorMessage}</Typography>
                  </DialogContentText>
                <ReasonField
                  label="Reason"
                  helperText="Enter the reason for killing the gene"
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
                    className={this.props.classes.killButton}
                  >
                    KILL {this.props.geneName}
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

KillGeneDialog.propTypes = {
  geneName: PropTypes.string.isRequired,
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmitSuccess: PropTypes.func,
  onSubmitError: PropTypes.func,
};

const styles = (theme) => ({
  killButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(KillGeneDialog);
