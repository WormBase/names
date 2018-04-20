import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles, Button, Icon } from '../../components/elements';
import GeneForm from './GeneForm';

const GeneProfile = (props) => {
  const {classes} = props;
  return (
    <div className={classes.root}>
      <div className={classes.left}>
        <Button
          variant="raised"
          component={({...props}) => <Link to='/gene' {...props} />}
        >
          Back to directory
        </Button>
      </div>
      <div className={classes.main}>
        <h2>Add Gene</h2>
        <GeneForm />
      </div>
      <div className={classes.right}>
        right
      </div>
    </div>
  );
}

GeneProfile.propTypes = {
  classes: PropTypes.object.isRequired,
};

const styles = (theme) => ({
  root: {
    display: 'flex',
    flexWrap: 'wrap',
  },
  left: {
    minWidth: '20%',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
    },
  },
  main: {
    flexGrow: 1,
     margin: `0px ${theme.spacing.unit * 10}px`,
  },
  right: {
    minWidth: '20%',
    [theme.breakpoints.down('sm')]: {
      width: `100%`,
    },
  },
});

export default withStyles(styles)(GeneProfile);
