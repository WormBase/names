import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import Button from './Button';
import TextField from './TextField';
import ProgressButton, {
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
} from './ProgressButton';
import BaseForm from './BaseForm';
import SimpleAjax from './SimpleAjax';

class EntityCreateForm extends Component {
  render() {
    const {
      classes,
      entityType,
      data,
      submitter,
      create = false,
      disabled = false,
      ...others
    } = this.props;
    return (
      <BaseForm data={data}>
        {({ withFieldData, getFormData, dirtinessContext, resetData }) => {
          const ReasonField = withFieldData(TextField, 'provenance/why');
          return (
            <SimpleAjax data={getFormData} submitter={submitter} {...others}>
              {({ status, errorMessage, handleSubmit }) => {
                return (
                  <div>
                    <div>
                      {this.props.children({
                        // from BaseForm
                        withFieldData,
                        getFormData,
                        // from SimpleAjax
                        status,
                        errorMessage,
                        handleSubmit,
                      })}
                      {dirtinessContext(({ dirty }) =>
                        dirty ? (
                          <ReasonField
                            label="Reason"
                            helperText={
                              create
                                ? `Why do you create this ${entityType}?`
                                : `Why do you edit this gene ${entityType}?`
                            }
                          />
                        ) : null
                      )}
                    </div>
                    {dirtinessContext(({ dirty }) => (
                      <div className={classes.actions}>
                        <Button
                          variant="raised"
                          onClick={resetData}
                          disabled={disabled}
                        >
                          Reset
                        </Button>
                        <ProgressButton
                          status={
                            status === 'SUBMITTED'
                              ? PROGRESS_BUTTON_PENDING
                              : PROGRESS_BUTTON_READY
                          }
                          variant="raised"
                          color="secondary"
                          onClick={() => submitter(dirty ? getFormData() : {})}
                          disabled={status === 'SUBMITTED' || disabled}
                        >
                          {create ? 'Create' : 'Update'}
                        </ProgressButton>
                      </div>
                    ))}
                  </div>
                );
              }}
            </SimpleAjax>
          );
        }}
      </BaseForm>
    );
  }
}

EntityCreateForm.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  data: PropTypes.any,
  submitter: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
  create: PropTypes.bool,
};

const styles = (theme) => ({
  actions: {
    marginTop: theme.spacing.unit * 2,
    '& > *': {
      marginRight: theme.spacing.unit,
      width: 150,
      [theme.breakpoints.down('xs')]: {
        width: `calc(50% - ${theme.spacing.unit}px)`,
      },
    },
  },
});

export default withStyles(styles)(EntityCreateForm);
