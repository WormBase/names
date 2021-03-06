import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';
import { capitalize } from '../../utils/format';
import {
  Button,
  DocumentTitle,
  ErrorBoundary,
  Page,
  PageMain,
  // SearchBox,
} from '../../components/elements';
import EntityRecentActivities from './EntityRecentActivities';

const EntityDirectory = (props) => {
  const {
    classes,
    entityType,
    renderHistory = () => <EntityRecentActivities entityType={entityType} />, //<i>Coming soon...</i>,
  } = props;
  return (
    <DocumentTitle title={`${capitalize(entityType)} directory`}>
      <Page>
        <PageMain>
          <div className={classes.root}>
            <div className={classes.header}>
              <Button
                variant="raised"
                color="secondary"
                component={({ ...props }) => (
                  <Link to={`/${entityType}/new`} {...props} />
                )}
              >
                Add new {entityType}
              </Button>
              {/*
                <div className={classes.or}>OR</div>
                  <div className={classes.search}>
                  <SearchBox entityType={entityType} />
                </div>
                */}
            </div>
          </div>
          <div className={classes.main}>
            {/* tables and charts */}
            {
              <section>
                <Typography variant="title" gutterBottom>
                  Recent activities
                </Typography>
                <ErrorBoundary>{renderHistory()}</ErrorBoundary>
              </section>
            }
          </div>
        </PageMain>
      </Page>
    </DocumentTitle>
  );
};

EntityDirectory.propTypes = {
  classes: PropTypes.object.isRequired,
  entityType: PropTypes.string.isRequired,
  renderHistory: PropTypes.func,
};

const styles = (theme) => ({
  root: {
    flexGrow: 1,
  },
  header: {
    display: 'flex',
    justifyContent: 'start',
    alignItems: 'center',
    marginBottom: theme.spacing.unit * 6,
    [theme.breakpoints.down('xs')]: {
      flexDirection: 'column',
    },
  },
  or: {
    [theme.breakpoints.down('xs')]: {
      display: 'none',
    },
  },
  search: {
    marginTop: theme.spacing.unit * 2,
  },
  main: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    '& > *': {
      width: '100%',
    },
  },
});

export default withStyles(styles)(EntityDirectory);
