import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Icon, Typography } from '../../components/elements';
import GeneForm from './GeneForm';
import KillGeneDialog from './KillGeneDialog';

class GeneProfile extends Component {
  constructor(props) {
    super(props);
    this.state = {
      status: null,
      data: null,
      showKillGeneDialog: false,
      killGeneDialogError: null,
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
        this.setState({
          data: response,
          status: 'SUCCESS',
        });
      }).catch((e) => console.log('error', e));
    });
  }

  openKillGeneDialog = () => {
    this.setState({
      showKillGeneDialog: true,
    });
  }

  closeKillGeneDialog = () => {
    this.setState({
      showKillGeneDialog: false,
    });
  }

  killGene = (data) => {
    mockFetchOrNot(
      (mockFetch) => {
        console.log(data.reason);
        if (data.reason) {
          return mockFetch.delete('*', {
            id: this.props.wbId,
            reason: data.reason,
            dead: true,
          });
        } else {
          return mockFetch.delete('*', {
            body: {
              error: 'Reason for killing a gene is required',
            },
            status: 400,
          })
        }
      },
      () => {
        return fetch(`/api/gene/${this.props.wbId}`, {
          method: 'DELETE'
        });
      },
      true
    ).then((response) => response.json()).then((response) => {
      if (!response.error) {
        this.setState({
          data: {...response},
          killGeneDialogError: null,
        }, () => {
          this.closeKillGeneDialog();
        });
      } else {
        this.setState({
          killGeneDialogError: response.error,
        });
      }
    }).catch((e) => console.log('error', e));
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
                <Button
                  className={classes.killButton}
                  variant="raised"
                  onClick={this.openKillGeneDialog}
                >Kill Gene</Button>
              </div> :
              null
          }
        </div>
        <KillGeneDialog
          geneName={this.state.data && this.state.data.cgcName}
          errorMessage={this.state.killGeneDialogError}
          open={this.state.showKillGeneDialog}
          onClose={this.closeKillGeneDialog}
          onSubmit={this.killGene}
        />
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
