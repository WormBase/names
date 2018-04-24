import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { withStyles, Button, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';

const GeneProfile = (props) => {
  const {classes, wbId} = props;
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
        <Typography variant="headline" gutterBottom>Add Gene</Typography>
        <GeneForm />
      </div>
      <div className={classes.right}>
        {
          wbId ?
            <div className={classes.operations}>
              <Button variant="raised">Split Gene</Button>
              <Button variant="raised">Merge Gene</Button>
              <Button className={classes.killButton} variant="raised">Kill Gene</Button>
            </div> :
            null
        }
      </div>
    </div>
  );
}

GeneProfile.propTypes = {
  classes: PropTypes.object.isRequired,
  wbId: PropTypes.string,
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
  operations: {
    display: 'flex',
    flexDirection: 'column',
    maxWidth: 150,
    '& > *': {
      marginBottom: theme.spacing.unit,
    },
  },
  killButton: {
    backgroundColor: theme.palette.error.main,
    color: theme.palette.error.contrastText,
  }
});

export default withStyles(styles)(GeneProfile);
