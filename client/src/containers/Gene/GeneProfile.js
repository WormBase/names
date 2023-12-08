import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { Button, Humanize, Typography } from '../../components/elements';
import {
  EntityProfile,
  EntityDialogKill,
  EntityDialogResurrect,
  EntityHistory,
} from '../Entity';
import { pastTense, getActivityDescriptor } from '../../utils/events';
import GeneForm from './GeneForm';
import SuppressGeneDialog from './SuppressGeneDialog';
import MergeGeneDialog from './MergeGeneDialog';
import SplitGeneDialog from './SplitGeneDialog';

const OPERATION_KILL = 'kill';
const OPERATION_RESURRECT = 'resurrect';
const OPERATION_SUPPRESS = 'suppress';
const OPERATION_MERGE = 'merge';
const OPERATION_SPLIT = 'split';

class GeneProfile extends Component {
  getDisplayName = (data = {}) =>
    data['cgc-name'] || data['sequence-name'] || data['id'];

  renderStatus = ({ data = {}, changes = [] }) => {
    const killEventDescriptor =
      data['status'] === 'dead' ? getActivityDescriptor(changes[0]) : {};

    return data['status'] !== 'live' ? (
      <Typography variant="display1" gutterBottom>
        <Humanize capitalized>{data['status']}</Humanize>
        {data['status'] === 'dead' ? (
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
    const { wbId, entityType } = this.props;
    return (
      <EntityHistory wbId={wbId} activities={changes} entityType={entityType} />
    );
  };

  render() {
    const { wbId, entityType } = this.props;
    return (
      <EntityProfile
        wbId={wbId}
        entityType={entityType}
        apiPrefix="/api/gene"
        renderDisplayName={this.getDisplayName}
        renderStatus={this.renderStatus}
        renderOperations={({
          data,
          changes,
          getOperationProps,
          getDialogProps,
        }) => {
          const live = data['status'] === 'live';
          const dead = data['status'] === 'dead';
          return (
            <React.Fragment>
              {!dead && (
                <Button
                  {...getOperationProps(OPERATION_MERGE)}
                  variant="contained"
                >
                  Merge Gene
                </Button>
              )}
              {!dead && (
                <Button
                  {...getOperationProps(OPERATION_SPLIT)}
                  variant="contained"
                >
                  Split Gene
                </Button>
              )}
              {live && (
                <Button
                  {...getOperationProps(OPERATION_SUPPRESS)}
                  variant="contained"
                >
                  Suppress Gene
                </Button>
              )}
              {!dead && (
                <Button
                  {...getOperationProps(OPERATION_KILL)}
                  wbVariant="danger"
                  variant="contained"
                >
                  Kill Gene
                </Button>
              )}
              {dead && (
                <Button
                  {...getOperationProps(OPERATION_RESURRECT)}
                  wbVariant="danger"
                  variant="contained"
                >
                  Resurrect Gene
                </Button>
              )}
              <EntityDialogKill {...getDialogProps(OPERATION_KILL)} />
              <EntityDialogResurrect {...getDialogProps(OPERATION_RESURRECT)} />
              <SuppressGeneDialog {...getDialogProps(OPERATION_SUPPRESS)} />
              <MergeGeneDialog {...getDialogProps(OPERATION_MERGE)} />
              <SplitGeneDialog {...getDialogProps(OPERATION_SPLIT)} />
            </React.Fragment>
          );
        }}
        renderForm={({ data, changes, ...props }) => (
          <GeneForm
            {...props}
            cloned={Boolean(data['sequence-name'] || data['biotype'])}
          />
        )}
        renderOperationTip={({ data, Wrapper }) =>
          data['status'] === 'suppressed' ? (
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
  entityType: PropTypes.string.isRequired,
};

export default GeneProfile;
