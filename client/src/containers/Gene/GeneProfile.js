import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import {
  Button,
  EntityEditForm,
  EntityProfile,
  EntityNotFound,
  Humanize,
  Typography,
} from '../../components/elements';
import { pastTense, getActivityDescriptor } from '../../utils/events';
import GeneForm from './GeneForm';
import KillGeneDialog from './KillGeneDialog';
import ResurrectGeneDialog from './ResurrectGeneDialog';
import SuppressGeneDialog from './SuppressGeneDialog';
import MergeGeneDialog from './MergeGeneDialog';
import SplitGeneDialog from './SplitGeneDialog';
import RecentActivitiesSingleGene from './RecentActivitiesSingleGene';

class GeneProfile extends Component {
  getDisplayName = (data = {}) =>
    data['gene/cgc-name'] || data['gene/sequence-name'] || data['gene/id'];

  renderStatus = (data = {}, changes = []) => {
    const killEventDescriptor =
      data['gene/status'] === 'gene.status/dead'
        ? getActivityDescriptor(changes[0])
        : {};

    return data['gene/status'] !== 'gene.status/live' ? (
      <Typography variant="display1" gutterBottom>
        <Humanize capitalized>{data['gene/status']}</Humanize>
        {data['gene/status'] === 'gene.status/dead' ? (
          <Typography variant="subheading" component={'i'}>
            (
            <Humanize postProcessor={pastTense}>
              {killEventDescriptor.eventLabel}
            </Humanize>
            {killEventDescriptor.relatedEntity ? (
              <React.Fragment>
                {' '}
                <Link
                  to={`/gene/id/${
                    killEventDescriptor.relatedEntity['gene/id']
                  }`}
                >
                  {killEventDescriptor.relatedEntity['gene/id']}
                </Link>
              </React.Fragment>
            ) : null}
            )
          </Typography>
        ) : null}
      </Typography>
    ) : null;
  };

  renderOperationTip = () => {
    return <p>To un-suppress the gene, kill then resurrect it.</p>;
  };

  renderChanges = (data = {}, changes = []) => {
    const { authorizedFetch } = this.props;
    console.log(data);
    return (
      <RecentActivitiesSingleGene
        wbId={data['gene/id']}
        authorizedFetch={authorizedFetch}
        activities={changes}
        onUpdate={() => {
          this.fetchData();
        }}
      />
    );
  };

  render() {
    const { authorizedFetch, wbId } = this.props;
    return (
      <EntityEditForm
        wbId={wbId}
        entityType={'gene'}
        renderDisplayName={this.getDisplayName}
        authorizedFetch={authorizedFetch}
      >
        {({
          withFieldData,
          dirtinessContext,
          getProfileProps,
          getDialogProps,
          getOperationProps,
          dataCommitted: data = {},
          changes = [],
        }) => (
          <EntityProfile
            {...getProfileProps()}
            renderStatus={() => this.renderStatus(data, changes)}
            renderOperations={() => {
              const live = data['gene/status'] === 'gene.status/live';
              const dead = data['gene/status'] === 'gene.status/dead';
              return (
                <React.Fragment>
                  {!dead && (
                    <Button {...getOperationProps('merge')} variant="raised">
                      Merge Gene
                    </Button>
                  )}
                  {!dead && (
                    <Button {...getOperationProps('split')} variant="raised">
                      Split Gene
                    </Button>
                  )}
                  {live && (
                    <Button {...getOperationProps('suppress')} variant="raised">
                      Suppress Gene
                    </Button>
                  )}
                  {!dead && (
                    <Button
                      {...getOperationProps('kill')}
                      wbVariant="danger"
                      variant="raised"
                    >
                      Kill Gene
                    </Button>
                  )}
                  {dead && (
                    <Button
                      {...getOperationProps('resurrect')}
                      wbVariant="danger"
                      variant="raised"
                    >
                      Resurrect Gene
                    </Button>
                  )}
                </React.Fragment>
              );
            }}
            renderForm={() => (
              <React.Fragment>
                <GeneForm
                  withFieldData={withFieldData}
                  dirtinessContext={dirtinessContext}
                  cloned={Boolean(
                    data['gene/sequence-name'] || data['gene/biotype']
                  )}
                />
                <KillGeneDialog {...getDialogProps('kill')} />
                <ResurrectGeneDialog {...getDialogProps('resurrect')} />
                <SuppressGeneDialog {...getDialogProps('suppress')} />
                <MergeGeneDialog {...getDialogProps('merge')} />
                <SplitGeneDialog {...getDialogProps('split')} />
              </React.Fragment>
            )}
            renderOperationTip={this.renderOperationTip}
            renderChanges={() => this.renderChanges(data, changes)}
          />
        )}
      </EntityEditForm>
    );
  }
}

GeneProfile.propTypes = {
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func.isRequired,
};

export default GeneProfile;
