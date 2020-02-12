import React, { Component } from 'react';
import PropTypes from 'prop-types';
import {
  withStyles,
  Button,
  Dialog,
  DialogActions,
  DialogTitle,
} from '@material-ui/core';
import BaseForm from './BaseForm';
import ProgressButton, {
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
} from './ProgressButton';
import SimpleAjax from './SimpleAjax';

class AjaxDialog extends Component {
  handleClose = (reset) => {
    reset();
    this.props.onClose && this.props.onClose();
  };

  render() {
    return (
      <BaseForm data={this.props.data}>
        {({ withFieldData, getFormData, resetData }) => {
          return (
            <SimpleAjax
              data={getFormData}
              submitter={this.props.submitter}
              onSubmitSuccess={(result) => {
                this.props.onSubmitSuccess &&
                  this.props.onSubmitSuccess(result);
                resetData();
              }}
              onSubmitError={this.props.onSubmitError}
            >
              {({ status, errorMessage, handleSubmit }) => {
                return (
                  <Dialog
                    open={this.props.open}
                    onClose={() => this.handleClose(resetData)}
                    fullWidth
                    maxWidth="sm"
                    aria-labelledby="form-dialog-title"
                  >
                    <DialogTitle id="form-dialog-title">
                      {this.props.title}
                    </DialogTitle>
                    {this.props.children({
                      // from BaseForm
                      withFieldData,
                      getFormData,
                      resetData,
                      // from SimpleAjax
                      status,
                      errorMessage,
                      handleSubmit,
                    })}
                    <DialogActions>
                      <Button
                        onClick={() => this.handleClose(resetData)}
                        //    color="primary"
                      >
                        Cancel
                      </Button>
                      {(this.props.renderSubmitButton ||
                        ((props) => <ProgressButton {...props} />))({
                        status:
                          status === 'SUBMITTED'
                            ? PROGRESS_BUTTON_PENDING
                            : PROGRESS_BUTTON_READY,
                        color: 'primary',
                        onClick: handleSubmit,
                        className: this.props.classes.submitButton,
                        children: 'Submit',
                      })}
                    </DialogActions>
                  </Dialog>
                );
              }}
            </SimpleAjax>
          );
        }}
      </BaseForm>
    );
  }
}

AjaxDialog.propTypes = {
  classes: PropTypes.object.isRequired,
  title: PropTypes.string,
  data: PropTypes.any,
  submitter: PropTypes.any,
  open: PropTypes.any,
  onClose: PropTypes.func,
  onSubmitSuccess: PropTypes.any,
  onSubmitError: PropTypes.any,
  renderSubmitButton: PropTypes.func,
};

const styles = (theme) => ({
  submitButton: {
    textTransform: 'inherit',
  },
});

export default withStyles(styles)(AjaxDialog);
