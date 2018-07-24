import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { authorizedFetch } from '../Authenticate';
import { withStyles, Button, Icon, Page, PageLeft, PageMain, PageRight, Typography } from '../../components/elements';
import GeneForm from './GeneForm';
import KillGeneDialog from './KillGeneDialog';
import MergeGeneDialog from './MergeGeneDialog';
import SplitGeneDialog from './SplitGeneDialog';
import RecentActivitiesSingleGene from './RecentActivitiesSingleGene';

class GeneProfile extends Component {
  constructor(props) {
    super(props);
    this.state = {
      status: null,
      data: null,
      showKillGeneDialog: false,
      showMergeGeneDialog: false,
      showSplitGeneDialog: false,
    };
  }

  componentDidMount() {
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', {
            "gene/species": {
               "species/id":"species/c-elegans",
               "species/latin-name":"Caenorhabditis elegans",
               "species/cgc-name-pattern":"^[a-z21]{3,4}-[1-9]{1}\\d*",
               "species/sequence-name-pattern":"^[A-Z0-9_cel]+\\.[1-9]\\d{0,3}[A-Za-z]?$"
            },
            "gene/cgc-name":"abi-1",
            "gene/status":"gene.status/live",
            "gene/biotype":"biotype/cds",
            "gene/id": this.props.wbId,
          });
        },
        () => {
          console.log('zzzzzz: ' + `/api/gene/${this.props.wbId}`);
          return authorizedFetch(`/api/gene/${this.props.wbId}`, {});
        },
      ).then((response) => response.json()).then((response) => {
        console.log(response);
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

  openMergeGeneDialog = () => {
    this.setState({
      showMergeGeneDialog: true,
    });
  }

  closeMergeGeneDialog = () => {
    this.setState({
      showMergeGeneDialog: false,
    });
  }

  openSplitGeneDialog = () => {
    this.setState({
      showSplitGeneDialog: true,
    });
  }

  closeSplitGeneDialog = () => {
    this.setState({
      showSplitGeneDialog: false,
    });
  }

  render() {
    const {classes, wbId} = this.props;
    return (
      <Page>
        <PageLeft>
          <Button
            variant="raised"
            component={({...props}) => <Link to='/gene' {...props} />}
          >
            Back to directory
          </Button>
        </PageLeft>
        <PageMain>
          <Typography variant="headline" gutterBottom>Gene <em>{wbId}</em></Typography>
          {
            this.state.status === 'SUCCESS' && !this.state.data.dead ?
              <GeneForm
                data={this.state.data}
                onSubmit={(data) => this.setState({
                  data: data,
                })}
              /> :
              <Typography variant="caption">Dead</Typography>
          }
          <div className={classes.section}>
            <Typography variant="title" gutterBottom>Change history</Typography>
            <div className={classes.historyTable}>
              <RecentActivitiesSingleGene wbId={this.props.wbId} />
            </div>
          </div>
        </PageMain>
        <PageRight>
          {
            this.state.data && this.state.data.dead ?
              null :
              <div className={classes.operations}>
                <Button
                  variant="raised"
                  onClick={this.openMergeGeneDialog}
                >Merge Gene</Button>
                <Button
                  variant="raised"
                  onClick={this.openSplitGeneDialog}
                >Split Gene</Button>
                <Button
                  className={classes.killButton}
                  variant="raised"
                  onClick={this.openKillGeneDialog}
                >Kill Gene</Button>
              </div>
          }
        </PageRight>
        {
          this.state.data ?
            <KillGeneDialog
              geneName={this.state.data.cgcName}
              open={this.state.showKillGeneDialog}
              onClose={this.closeKillGeneDialog}
              onSubmitSuccess={(data) => {
                this.setState({
                  data: data,
                }, () => {
                  this.closeKillGeneDialog();
                });
              }}
            /> :
            null
        }
        {
          this.state.data ?
            <MergeGeneDialog
              geneName={this.state.data.cgcName}
              open={this.state.showMergeGeneDialog}
              onClose={this.closeMergeGeneDialog}
              onSubmitSuccess={(data) => {
                this.setState({
                  data: data,
                }, () => {
                  this.closeMergeGeneDialog();
                });
              }}
            /> :
            null
        }
        {
          this.state.data ?
            <SplitGeneDialog
              geneName={this.state.data.cgcName}
              biotypeOriginal={this.state.data.biotype}
              open={this.state.showSplitGeneDialog}
              onClose={this.closeSplitGeneDialog}
              onSubmitSuccess={(data) => {
                this.setState({
                  data: data,
                }, () => {
                  this.closeSplitGeneDialog();
                });
              }}
            /> :
            null
        }
      </Page>
    );
  }
}

GeneProfile.propTypes = {
  classes: PropTypes.object.isRequired,
  wbId: PropTypes.string.isRequired,
};

const styles = (theme) => ({
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
  },
  section: {
    margin: `${theme.spacing.unit * 8}px 0`,
  },
  historyTable: {
    [theme.breakpoints.down('sm')]: {
      overflow: 'scroll',
    },
  },
});

export default withStyles(styles)(GeneProfile);
