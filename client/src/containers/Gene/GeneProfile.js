import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { mockFetchOrNot } from '../../mock';
import { withStyles, Button, Icon, Page, PageLeft, PageMain, PageRight, Typography } from '../../components/elements';
import GeneForm from './GeneForm';
import KillGeneDialog from './KillGeneDialog';

class GeneProfile extends Component {
  constructor(props) {
    super(props);
    this.state = {
      status: null,
      data: null,
      showKillGeneDialog: false,
    };
  }

  componentDidMount() {
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      mockFetchOrNot(
        (mockFetch) => {
          return mockFetch.get('*', {
            id: this.props.wbId,
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
        </PageMain>
        <PageRight>
          {
            this.state.data && this.state.data.dead ?
              null :
              <div className={classes.operations}>
                {/* <Button variant="raised">Split Gene</Button> */}
                {/* <Button variant="raised">Merge Gene</Button> */}
                <Button
                  className={classes.killButton}
                  variant="raised"
                  onClick={this.openKillGeneDialog}
                >Kill Gene</Button>
              </div>
          }
        </PageRight>
        <KillGeneDialog
          geneName={this.state.data && this.state.data.cgcName}
          open={this.state.showKillGeneDialog}
          onClose={this.closeKillGeneDialog}
          onKillSuccess={(data) => {
            this.setState({
              data: data,
            }, () => {
              this.closeKillGeneDialog();
            });
          }}
        />
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
  }
});

export default withStyles(styles)(GeneProfile);
