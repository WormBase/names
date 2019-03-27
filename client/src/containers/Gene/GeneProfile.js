import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Button, Humanize, Typography } from '../../components/elements';
import { EntityEdit, EntityProfile, EntityNotFound } from '../Entity';
import { pastTense, getActivityDescriptor } from '../../utils/events';
import GeneForm from './GeneForm';
import KillGeneDialog from './KillGeneDialog';
import ResurrectGeneDialog from './ResurrectGeneDialog';
import SuppressGeneDialog from './SuppressGeneDialog';
import MergeGeneDialog from './MergeGeneDialog';
import SplitGeneDialog from './SplitGeneDialog';
import RecentActivitiesSingleGene from './RecentActivitiesSingleGene';

const OPERATION_KILL = 'kill';
const OPERATION_RESURRECT = 'resurrect';
const OPERATION_SUPPRESS = 'suppress';
const OPERATION_MERGE = 'merge';
const OPERATION_SPLIT = 'split';

class GeneProfile extends Component {
  getDisplayName = (data = {}) =>
    data['gene/cgc-name'] || data['gene/sequence-name'] || data['gene/id'];

  renderStatus = ({ data = {}, changes = [] }) => {
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

  renderChanges = ({ data = {}, changes = [] }) => {
    console.log(data);
    return (
      <RecentActivitiesSingleGene
        wbId={data['gene/id']}
        activities={changes}
        onUpdate={() => {
          this.fetchData();
        }}
      />
    );
  };

  render() {
    const { wbId } = this.props;
    return (
      <EntityProfile
        wbId={wbId}
        entityType={'gene'}
        renderDisplayName={this.getDisplayName}
        renderStatus={this.renderStatus}
        renderOperations={({
          data,
          changes,
          getOperationProps,
          getDialogProps,
        }) => {
          const live = data['gene/status'] === 'gene.status/live';
          const dead = data['gene/status'] === 'gene.status/dead';
          return (
            <React.Fragment>
              {!dead && (
                <Button
                  {...getOperationProps(OPERATION_MERGE)}
                  variant="raised"
                >
                  Merge Gene
                </Button>
              )}
              {!dead && (
                <Button
                  {...getOperationProps(OPERATION_SPLIT)}
                  variant="raised"
                >
                  Split Gene
                </Button>
              )}
              {live && (
                <Button
                  {...getOperationProps(OPERATION_SUPPRESS)}
                  variant="raised"
                >
                  Suppress Gene
                </Button>
              )}
              {!dead && (
                <Button
                  {...getOperationProps(OPERATION_KILL)}
                  wbVariant="danger"
                  variant="raised"
                >
                  Kill Gene
                </Button>
              )}
              {dead && (
                <Button
                  {...getOperationProps(OPERATION_RESURRECT)}
                  wbVariant="danger"
                  variant="raised"
                >
                  Resurrect Gene
                </Button>
              )}
              <KillGeneDialog {...getDialogProps(OPERATION_KILL)} />
              <ResurrectGeneDialog {...getDialogProps(OPERATION_RESURRECT)} />
              <SuppressGeneDialog {...getDialogProps(OPERATION_SUPPRESS)} />
              <MergeGeneDialog {...getDialogProps(OPERATION_MERGE)} />
              <SplitGeneDialog {...getDialogProps(OPERATION_SPLIT)} />
            </React.Fragment>
          );
        }}
        renderForm={({ data, changes, ...props }) => (
          <GeneForm
            {...props}
            cloned={Boolean(data['gene/sequence-name'] || data['gene/biotype'])}
          />
        )}
        renderOperationTip={({ data, Wrapper }) =>
          data['gene/status'] === 'gene.status/suppressed' ? (
            <Wrapper>
              <p>To un-suppress the gene, kill then resurrect it.</p>
            </Wrapper>
          ) : null
        }
        renderChanges={this.renderChanges}
      />
    );
  }
}

GeneProfile.propTypes = {
  wbId: PropTypes.string.isRequired,
};

export default GeneProfile;
