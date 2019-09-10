import React from 'react';
import PropTypes from 'prop-types';
import {
  Page,
  PageMain,
  Paper,
  Button,
  MuiThemeProvider,
  withStyles,
} from '../../components/elements';
import { Link } from 'react-router-dom';
import { ENTITY_TYPES } from '../../utils/entityTypes';
console.log(ENTITY_TYPES);

function Home({ classes }) {
  return (
    <Page>
      <PageMain>
        <div className={classes.main}>
          {ENTITY_TYPES.map(({ entityType, path, theme }) => (
            <Paper elevation={1} className={classes.row}>
              <div className={classes.cell}>
                <MuiThemeProvider theme={theme}>
                  <Button
                    variant="raised"
                    color="secondary"
                    component={({ ...props }) => (
                      <Link to={`${path}/new`} {...props} />
                    )}
                  >
                    Add {entityType}
                  </Button>
                </MuiThemeProvider>
              </div>
              <div className={classes.cell}>
                <Button
                  color="primary"
                  component={({ ...props }) => <Link to={path} {...props} />}
                >
                  Browse {entityType}s
                </Button>
              </div>
            </Paper>
          ))}
        </div>
      </PageMain>
    </Page>
  );
}

Home.propTypes = {
  classes: PropTypes.object.isRequired,
};
const styles = (theme) => ({
  main: {
    maxWidth: 600,
    margin: `0 auto`,
  },
  row: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    margin: `${theme.spacing.unit * 2}px 0`,
  },
  cell: {
    padding: `${theme.spacing.unit * 4}px`,
    flex: `1 1 50%`,
    display: 'flex',
    '&:first-child > *': {
      width: '100%',
    },
  },
});
export default withStyles(styles)(Home);
