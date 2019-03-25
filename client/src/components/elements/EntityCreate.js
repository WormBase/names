import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';
import Button from './Button';
import ProgressButton from './ProgressButton';
import DocumentTitle from './DocumentTitle';
import EntityDirectoryButton from './EntityDirectoryButton';
import ErrorBoundary from './ErrorBoundary';
import { Page, PageLeft, PageMain } from './Page';
import TextField from './TextField';
import ValidationError from './ValidationError';

class EntityCreate extends Component {
  render() {
    const {
      classes = {},
      entityType,
      errorMessage = null,
      withFieldData,
      dirtinessContext,
      renderForm,
      buttonResetProps,
      buttonSubmitProps,
    } = this.props;

    const ReasonField = withFieldData(TextField, 'provenance/why');

    return (
      <DocumentTitle title={`Create ${entityType}`}>
        <Page>
          <PageLeft>
            <div className={classes.operations}>
              <EntityDirectoryButton entityType={entityType} />
            </div>
          </PageLeft>
          <PageMain>
            <Typography variant="headline" gutterBottom>
              Add {entityType}
            </Typography>
            <ValidationError {...errorMessage} />
            {renderForm ? (
              <ErrorBoundary>
                {renderForm()}
                {dirtinessContext(({ dirty }) =>
                  dirty ? (
                    <ReasonField
                      label="Reason"
                      helperText={`Why do you create this gene?`}
                    />
                  ) : null
                )}
              </ErrorBoundary>
            ) : null}
            <div className={classes.actions}>
              <Button variant="raised" {...buttonResetProps}>
                Reset
              </Button>
              <ProgressButton
                variant="raised"
                color="secondary"
                {...buttonSubmitProps}
              >
                Create
              </ProgressButton>
            </div>
          </PageMain>
        </Page>
      </DocumentTitle>
    );
  }
}

EntityCreate.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  withFieldData: PropTypes.func.isRequired,
  dirtinessContext: PropTypes.func.isRequired,
  errorMessage: PropTypes.string,
  renderForm: PropTypes.func,
  buttonResetProps: PropTypes.func,
  buttonSubmitProps: PropTypes.func,
};

const styles = (theme) => ({
  operations: {
    display: 'flex',
    flexDirection: 'column',
    width: 150,
    '& > *': {
      marginBottom: theme.spacing.unit,
    },
    [theme.breakpoints.down('sm')]: {
      width: '100%',
      alignItems: 'stretch',
    },
  },
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

export default withStyles(styles)(EntityCreate);
