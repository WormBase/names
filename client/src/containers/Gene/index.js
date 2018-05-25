import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles, Button, Icon, Page, } from '../../components/elements';

import GeneProfile from './GeneProfile';
import GeneCreate from './GeneCreate';
import GeneSearchBox from './GeneSearchBox';

const Gene = (props) => {
  const {classes} = props;
  return (
    <Page>
      <div className={classes.root}>
      <div className={classes.header}>
        <Button
          variant="raised"
          color="secondary"
          component={({...props}) => <Link to='/gene/new' {...props} />}
        >
          Add New Gene
        </Button>
        OR
        <GeneSearchBox />
      </div>
      <div className={classes.main}>
        {/* tables and charts */}
      </div>
      </div>
    </Page>
  );
}

Gene.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    flexGrow: 1,
  },
  header: {
    display: 'flex',
    flexWrap: 'wrap',
    justifyContent: 'space-around',
    alignItems: 'center',
    marginBottom: theme.spacing.unit * 4,
  },
});

export default withStyles(styles)(Gene);

export {
  GeneProfile,
  GeneCreate,
};
