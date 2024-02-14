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
import { useEntityTypes } from '../Entity';
import { Link } from 'react-router-dom';

function Home({ classes }) {
  const { entityTypesAll } = useEntityTypes();
  return (
    <Page>
      <PageMain>
        <div className={classes.main}>
          {entityTypesAll.map(({ entityType, path, theme, displayName }) => {
            const new_entity_link = React.forwardRef(function(props, ref) {
              return <Link to={`${path}/new`} {...props} ref={ref} />;
            });
            const entity_directory_link = React.forwardRef(function(
              props,
              ref
            ) {
              return <Link to={path} {...props} ref={ref} />;
            });

            return (
              <Paper key={entityType} levation={1} className={classes.row}>
                <div className={classes.cell}>
                  <MuiThemeProvider theme={theme}>
                    <Button
                      variant="contained"
                      color="secondary"
                      component={new_entity_link}
                    >
                      Add {displayName}
                    </Button>
                  </MuiThemeProvider>
                </div>
                <div className={classes.cell}>
                  <Button color="primary" component={entity_directory_link}>
                    Browse {displayName}s
                  </Button>
                </div>
              </Paper>
            );
          })}
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
    margin: `${theme.spacing(2)}px 0`,
  },
  cell: {
    padding: `${theme.spacing(4)}px`,
    flex: `1 1 50%`,
    display: 'flex',
    '&:first-child > *': {
      width: '100%',
    },
  },
});
export default withStyles(styles)(Home);
