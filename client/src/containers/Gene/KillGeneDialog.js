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
                    Gene <strong>{this.props.geneName}</strong> will be killed. Are you sure?
                  </DialogContentText>
                  <DialogContentText>
                    <Typography color="error">{this.props.errorMessage}</Typography>
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
                    onClick={() => this.props.onSubmit(getFormData())}
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
  errorMessage: PropTypes.string,
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
