import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';
import Button from './Button';
import ErrorBoundary from './ErrorBoundary';
import Page, { PageMain } from './Page';
import SearchBox from './SearchBox';

const EntityDirectory = (props) => {
  const { classes, entityType, renderHistory } = props;
  return (
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
            <div className={classes.or}>OR</div>
            <div className={classes.search}>
              <SearchBox />
            </div>
          </div>
        </div>
        <div className={classes.main}>
          {/* tables and charts */}
          {renderHistory ? (
            <section>
              <Typography variant="title" gutterBottom>
                Recent activities
              </Typography>
              <ErrorBoundary>
                <div className={classes.recentActivitiesTable}>
                  {renderHistory()}
                </div>
              </ErrorBoundary>
            </section>
          ) : null}
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
    justifyContent: 'space-around',
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
  recentActivitiesTable: {
    overflow: 'scroll',
  },
});

export default withStyles(styles)(EntityDirectory);
