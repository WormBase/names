import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';

import EntityDirectoryButton from './EntityDirectoryButton';
import EntityEditNew from './EntityEditNew';
import EntityForm from './EntityForm';
import {
  BaseForm,
  Button,
  DocumentTitle,
  ErrorBoundary,
  Page,
  PageLeft,
  PageMain,
  ProgressButton,
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
  SimpleAjax,
  TextField,
  ValidationError,
} from '../../components/elements';

class EntityCreate extends Component {
  renderForm = (formProps) => <EntityForm {...formProps} />;

  render() {
    const {
      classes = {},
      entityType,
      renderForm = this.renderForm,
    } = this.props;

    return (
      <EntityEditNew entityType={entityType}>
        {({ profileContext, formContext }) => {
          const {
            errorMessage = null,
            withFieldData,
            dirtinessContext,
            buttonResetProps,
            buttonSubmitProps,
          } = profileContext;

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
                      {renderForm(formContext)}
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
        }}
      </EntityEditNew>
    );
  }
}

EntityCreate.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  renderForm: PropTypes.func,
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
