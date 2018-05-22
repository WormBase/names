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
            id: this.props.wbId,
            reason: data.reason,
            dead: true,
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
        return fetch(`/api/gene/${this.props.wbId}`, {
          method: 'DELETE'
        });
      },
      true
    ).then((response) => response.json()).then((response) => {
      if (!response.error) {
        this.props.onKillSuccess && this.props.onKillSuccess({...response});
      } else {
        this.setState({
          errorMessage: response.error,
        });
        this.props.onKillError && this.props.onKillError(response.error);
      }
    }).catch((e) => console.log('error', e));
  }

  render() {
    return (
      <BaseForm>
        {
          ({withFieldData, getFormData, resetData}) => {
            const ReasonField = withFieldData(TextField, 'reason');
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
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmit: PropTypes.func,
};

const styles = (theme) => ({
  killButton: {
    color: theme.palette.error.main,
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(KillGeneDialog);
