import React, { Component } from 'react';
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
                    Gene <strong>{'zzzz'}</strong> will be killed. Are you sure?
                  </DialogContentText>
                <ReasonField
                  label="Reason"
                  helperText="Enter the reason for killing the gene"
                  fullWidth
                />
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => this.onSubmit(getFormData())} color="primary">
                    Cancel
                  </Button>
                  <Button onClick={this.handleClose} color="primary">
                    Subscribe
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
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmit: PropTypes.func,
};

export default KillGeneDialog;
