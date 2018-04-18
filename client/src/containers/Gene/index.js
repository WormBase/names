import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles, Button, Icon } from '../../components/elements';
import BasicPage from '../../components/layout';

const Gene = (props) => {
  const {classes} = props;
  return (
    <BasicPage>
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
          <Button>This is supposed to be a text field</Button>
        </div>
        <div className={classes.main}>
          tables and charts
        </div>
      </div>
    </BasicPage>
  );
}

Gene.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
  },
  header: {
    display: 'flex',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    margin: theme.spacing.unit * 4,
  },
});

export default withStyles(styles)(Gene);

export {

};
