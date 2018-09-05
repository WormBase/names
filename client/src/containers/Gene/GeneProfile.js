import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link, withRouter } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import {
  withStyles,
  Button,
  CircularProgress,
  NotFound,
  Page,
  PageLeft,
  PageMain,
  PageRight,
  Snackbar,
  SnackbarContent,
  Typography
} from '../../components/elements';
import GeneForm from './GeneForm';
import KillGeneDialog from './KillGeneDialog';
import ResurrectGeneDialog from './ResurrectGeneDialog';
import MergeGeneDialog from './MergeGeneDialog';
import SplitGeneDialog from './SplitGeneDialog';
import RecentActivitiesSingleGene from './RecentActivitiesSingleGene';

class GeneProfile extends Component {
  constructor(props) {
    super(props);
    this.state = {
      status: null,
      errorMessage: null,
      successMessage: null,
      data: {},
      showKillGeneDialog: false,
      showResurrectGeneDialog: false,
      showMergeGeneDialog: false,
      showSplitGeneDialog: false,
    };
  }

  componentDidMount() {
    this.fetchData();
  }

  getId = () => {
    return this.state.data['gene/id'] || this.props.wbId;
  }

  fetchData = () => {
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          const historyMock = [
            {
              "provenance/how":"agent/web",
              "provenance/what":"event/merge-genes",
              "provenance/who":{
                "person/id":"WBPerson12346"
              },
              "provenance/when":"2018-08-09T22:09:16Z",
              "provenance/merged-from":{
                "gene/id":"WBGene00303223"
              },
              "provenance/merged-into":{
                "gene/id": this.getId()
              }
            },
            {
              "provenance/how":"agent/web",
              "provenance/what":"event/update-gene",
              "provenance/who":{
                "person/id":"WBPerson12346"
              },
              "provenance/when":"2018-08-08T17:27:31Z"
            },
            {
              "provenance/how":"agent/web",
              "provenance/what":"event/update-gene",
              "provenance/who":{
                "person/id":"WBPerson12346"
              },
              "provenance/when":"2018-08-08T17:27:22Z"
            },
            {
              "provenance/how":"agent/web",
              "provenance/what":"event/split-gene",
              "provenance/who":{
                "person/id":"WBPerson12346"
              },
              "provenance/when":"2018-08-08T16:50:46Z",
              "provenance/split-from":{
                "gene/id": this.getId()
              },
              "provenance/split-into":{
                "gene/id":"WBGene00303222"
              }
            },
            {
              "provenance/how":"agent/web",
              "provenance/what":"event/split-gene",
              "provenance/who":{
                "person/id":"WBPerson12346"
              },
              "provenance/when":"2018-08-08T15:21:07Z",
              "provenance/split-from":{
                "gene/id": this.getId()
              },
              "provenance/split-into":{
                "gene/id":"WBGene00303219"
              }
            },
            {
              "provenance/how":"agent/web",
              "provenance/what":"event/new-gene",
              "provenance/who":{
                "person/id":"WBPerson12346"
              },
              "provenance/when":"2018-07-23T15:25:17Z"
            }
          ];

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
            "gene/id": this.getId(),
            "history": historyMock,
          });
        },
        () => {
          return fetch(`/api/gene/${this.getId()}`, {});
        },
      ).then((response) => {
        const nextStatus = (response.status === 404 || response.status === 500) ?
          'NOT_FOUND' :
          'COMPLETE';
        return Promise.all([nextStatus, response.json()]);
      }).then(([nextStatus, response]) => {
        this.setState({
          data: response,
          status: nextStatus,
        }, () => {
          const permanentUrl = `/gene/id/${this.getId()}`;
          if (nextStatus === 'COMPLETE' && this.props.history.location.pathname !== permanentUrl) {
            this.props.history.replace(`/gene/id/${this.getId()}`);
          }
        });
      }).catch((e) => console.log('error', e));
    });
  }

  handleGeneUpdate = (data) => {
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.put('*', {
            updated: {
              ...data,
            },
          });
        },
        () => {
          return this.props.authorizedFetch(`/api/gene/${this.getId()}`, {
            method: 'PUT',
            body: JSON.stringify({
              ...data
            }),
          });
        },
      ).then((response) => response.json()).then((response) => {
        this.setState(() => {
          const stateChanges = {
            status: 'COMPLETE',
          };
          if (response.message) {
            return {
              ...stateChanges,
              errorMessage: JSON.stringify(response),
            }
          } else {
            this.fetchData();
            return {
              ...stateChanges,
              errorMessage: null,
              data: response.updated,
              successMessage: 'Update successful!',
            }
          }
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

  openResurrectGeneDialog = () => {
    this.setState({
      showResurrectGeneDialog: true,
    });
  }

  closeResurrectGeneDialog = () => {
    this.setState({
      showResurrectGeneDialog: false,
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

  closeSnackbar = () => {
    this.setState({
      successMessage: null,
    });
  }

  getDisplayName = (data) => (
    data['gene/cgc-name'] ||
    data['gene/sequence-name'] ||
    data['gene/id']
  )

  render() {
    const {classes} = this.props;
    const wbId = this.getId();
    const backToDirectoryButton = (
      <Button
        variant="raised"
        component={({...props}) => <Link to='/gene' {...props} />}
      >
        Back to directory
      </Button>
    );
    return this.state.status === 'NOT_FOUND' ? (
      <NotFound>
        <Typography>
          <strong>{wbId}</strong> does not exist
        </Typography>
        {backToDirectoryButton}
      </NotFound>
    ) : (
      <Page>
        <PageLeft>
          {backToDirectoryButton}
        </PageLeft>
        <PageMain>
          <Typography variant="headline" gutterBottom>Gene <em>{wbId}</em></Typography>
          <Typography color="error">{this.state.errorMessage}</Typography>
          {
            this.state.data['gene/status'] === 'gene.status/live' ?
              <GeneForm
                data={this.state.data}
                onSubmit={this.handleGeneUpdate}
                submitted={this.state.status === 'SUBMITTED'}
              /> : this.state.status !== 'COMPLETE' ?
              <CircularProgress /> :
              <div>
                <Typography variant="display1" gutterBottom>Dead</Typography>
                <GeneForm
                  data={this.state.data}
                  disabled={true}
                  onSubmit={this.handleGeneUpdate}
                />
              </div>
          }
          <div className={classes.section}>
            <Typography variant="title" gutterBottom>Change history</Typography>
            <div className={classes.historyTable}>
              <RecentActivitiesSingleGene
                wbId={wbId}
                authorizedFetch={this.props.authorizedFetch}
                activities={this.state.data.history}
                onUpdate={() => {
                  this.fetchData();
                }}
              />
            </div>
          </div>
        </PageMain>
        <PageRight>
          {
            this.state.data['gene/status'] === 'gene.status/live' ?
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
              </div> :
              <div className={classes.operations}>
                <Button
                  className={classes.killButton}
                  variant="raised"
                  onClick={this.openResurrectGeneDialog}
                >Resurrect Gene</Button>
              </div>
          }
        </PageRight>
        <KillGeneDialog
          geneName={this.getDisplayName(this.state.data)}
          wbId={wbId}
          authorizedFetch={this.props.authorizedFetch}
          open={this.state.showKillGeneDialog}
          onClose={this.closeKillGeneDialog}
          onSubmitSuccess={(data) => {
            this.setState({
              successMessage: 'Kill successful!',
            }, () => {
              this.fetchData();
              this.closeKillGeneDialog();
            });
          }}
        />
        <ResurrectGeneDialog
          geneName={this.getDisplayName(this.state.data)}
          wbId={wbId}
          authorizedFetch={this.props.authorizedFetch}
          open={this.state.showResurrectGeneDialog}
          onClose={this.closeResurrectGeneDialog}
          onSubmitSuccess={(data) => {
            this.setState((prevState) => ({
              data: {
                ...prevState.data,
                'gene/status': 'gene.status/live',
              },
            }), () => {
              this.setState({
                successMessage: 'Resurrect successful!',
              }, () => {
                this.fetchData();
                this.closeResurrectGeneDialog();
              });
            });
          }}
        />
        <MergeGeneDialog
          geneName={this.getDisplayName(this.state.data)}
          wbId={wbId}
          authorizedFetch={this.props.authorizedFetch}
          open={this.state.showMergeGeneDialog}
          onClose={this.closeMergeGeneDialog}
          onSubmitSuccess={(data) => {
            this.setState({
              successMessage: 'Merge successful!',
            }, () => {
              this.fetchData();
              this.closeMergeGeneDialog();
            });
          }}
        />
        <SplitGeneDialog
          geneName={this.getDisplayName(this.state.data)}
          wbId={wbId}
          biotypeOriginal={this.state.data['gene/biotype']}
          authorizedFetch={this.props.authorizedFetch}
          open={this.state.showSplitGeneDialog}
          onClose={this.closeSplitGeneDialog}
          onSubmitSuccess={(data) => {
            this.setState({
              successMessage: 'Split successful!',
            }, () => {
              this.fetchData();
              this.closeSplitGeneDialog();
            });
          }}
        />
        <Snackbar
          className={classes.snackbar}
          anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
          open={this.state.successMessage}
          onClose={this.closeSnackbar}
        >
          <SnackbarContent
            className={classes.snackbarContent}
            variant="success"
            message={<span>{this.state.successMessage}</span>}
          />
        </Snackbar>
      </Page>
    );
  }
}

GeneProfile.propTypes = {
  classes: PropTypes.object.isRequired,
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
  history: PropTypes.shape({
    replace: PropTypes.func.isRequired,
  }).isRequired,
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
  snackbar: {
    left: 0,
    right: 0,
    transform: 'unset',
  },
  snackbarContent: {
    flexGrow: 1,
    justifyContent: 'center',
    minWidth: 'unset',
    maxWidth: 'unset',
    borderRadius: 'unset',
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

export default withStyles(styles)(withRouter(GeneProfile));
