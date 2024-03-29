import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';

import EntityDirectoryButton from './EntityDirectoryButton';
import EntityEditNew from './EntityEditNew';
import EntityForm from './EntityForm';
import {
  Button,
  ErrorBoundary,
  Page,
  PageLeft,
  PageMain,
  ProgressButton,
  TextArea,
  ValidationError,
} from '../../components/elements';

class EntityCreate extends Component {
  renderForm = (formProps) => <EntityForm {...formProps} />;

  render() {
    const {
      classes = {},
      entityType,
      apiPrefix = `/api/entity/${entityType}`,
      renderForm = this.renderForm,
    } = this.props;

    return (
      <EntityEditNew entityType={entityType} apiPrefix={apiPrefix}>
        {({ profileContext, formContext }) => {
          const {
            errorMessage = null,
            withFieldData,
            dirtinessContext,
            buttonResetProps,
            buttonSubmitProps,
          } = profileContext;

          const ReasonField = withFieldData(TextArea, 'why');

          return (
            <Page title={`Create ${entityType}`}>
              <PageLeft>
                <div className={classes.operations}>
                  <EntityDirectoryButton entityType={entityType} />
                </div>
              </PageLeft>
              <PageMain>
                <Typography variant="h5" gutterBottom>
                  Add {entityType}
                </Typography>
                <ValidationError {...errorMessage} />
                {renderForm ? (
                  <ErrorBoundary>
                    {renderForm(formContext)}
                    {dirtinessContext(({ dirty }) =>
                      true ? (
                        <ReasonField
                          multiline
                          label="Reason"
                          helperText={`Why do you create this ${entityType}?`}
                        />
                      ) : null
                    )}
                  </ErrorBoundary>
                ) : null}
                <div className={classes.actions}>
                  <Button variant="contained" {...buttonResetProps}>
                    Reset
                  </Button>
                  <ProgressButton
                    variant="contained"
                    color="secondary"
                    {...buttonSubmitProps}
                  >
                    Create
                  </ProgressButton>
                </div>
              </PageMain>
            </Page>
          );
        }}
      </EntityEditNew>
    );
  }
}

EntityCreate.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  apiPrefix: PropTypes.string,
  renderForm: PropTypes.func,
};

const styles = (theme) => ({
  operations: {
    display: 'flex',
    flexDirection: 'column',
    width: 150,
    '& > *': {
      marginBottom: theme.spacing(1),
    },
    [theme.breakpoints.down('sm')]: {
      width: '100%',
      alignItems: 'stretch',
    },
  },
  actions: {
    marginTop: theme.spacing(2),
    '& > *': {
      marginRight: theme.spacing(1),
      width: 150,
      [theme.breakpoints.down('xs')]: {
        width: `calc(50% - ${theme.spacing(1)}px)`,
      },
    },
  },
});

export default withStyles(styles)(EntityCreate);
