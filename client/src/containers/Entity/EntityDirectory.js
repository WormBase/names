import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';
import { capitalize } from '../../utils/format';
import {
  Button,
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
  const new_entity_link = React.forwardRef((props, ref) => (
    <Link to={`/${entityType}/new`} {...props} ref={ref} />
  ));
  return (
    <Page title={`${capitalize(entityType)} directory`}>
      <PageMain>
        <div className={classes.root}>
          <div className={classes.header}>
            <Button
              variant="contained"
              color="secondary"
              component={new_entity_link}
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
              <Typography variant="h6" gutterBottom>
                Recent activities
              </Typography>
              <ErrorBoundary>{renderHistory()}</ErrorBoundary>
            </section>
          }
        </div>
      </PageMain>
    </Page>
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
    marginBottom: theme.spacing(6),
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
    marginTop: theme.spacing(2),
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
