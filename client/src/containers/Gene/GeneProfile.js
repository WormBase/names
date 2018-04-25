import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';

class GeneProfile extends Component {
  constructor(props) {
    super(props);
    this.state = {
      status: null,
      data: null,
    };
  }

  componentDidMount() {
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', {
            id: 'WB1',
            cgcName: 'ab',
            sequenceName: 'AB',
            species: 'Caenorhabditis elegans',
            biotype: 'cds',
          });
        },
        () => {
          return fetch('/api/gene');
        },
        true
      ).then((response) => response.json()).then((response) => {
        console.log(response);
        this.setState({
          data: response,
          status: 'SUCCESS',
        });
      }).catch((e) => console.log('error', e));
    });
  }

  render() {
    const {classes, wbId} = this.props;
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
          <Typography variant="headline" gutterBottom>{wbId ? 'Edit Gene' : 'Add gene'}</Typography>
          {
            this.state.status === 'SUCCESS' ?
              <GeneForm
                data={this.state.data}
                createMode={!Boolean(wbId)}
                onSubmit={(data) => this.setState({
                  data: data,
                })}
              /> :
              null
          }
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
    '&:hover': {
      backgroundColor: theme.palette.error.dark,
    },
  }
});

export default withStyles(styles)(GeneProfile);
