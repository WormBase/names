import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import FormLabel from '@material-ui/core/FormLabel';
import IconButton from '@material-ui/core/IconButton';
import Tooltip from '@material-ui/core/Tooltip';
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
import DialogGeneAddOtherName from './DialogGeneAddOtherName';
import DialogGeneDeleteOtherName from './DialogGeneDeleteOtherName';
import DeleteIcon from '@material-ui/icons/Delete';

const OPERATION_KILL = 'kill';
const OPERATION_RESURRECT = 'resurrect';
const OPERATION_SUPPRESS = 'suppress';
const OPERATION_MERGE = 'merge';
const OPERATION_SPLIT = 'split';
const OPERATION_ADD_NAMES_OTHER = 'add_names_other';
const OPERATION_DELETE_NAME_OTHER = 'delete_names_other';

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
              <EntityDialogKill {...getDialogProps(OPERATION_KILL)} />
              <EntityDialogResurrect {...getDialogProps(OPERATION_RESURRECT)} />
              <SuppressGeneDialog {...getDialogProps(OPERATION_SUPPRESS)} />
              <MergeGeneDialog {...getDialogProps(OPERATION_MERGE)} />
              <SplitGeneDialog {...getDialogProps(OPERATION_SPLIT)} />
              <DialogGeneAddOtherName
                {...getDialogProps(OPERATION_ADD_NAMES_OTHER)}
              />
              <DialogGeneDeleteOtherName
                {...getDialogProps(OPERATION_DELETE_NAME_OTHER)}
              />
            </React.Fragment>
          );
        }}
        renderForm={({ data, changes, getOperationProps, ...props }) => (
          <GeneForm
            {...props}
            cloned={Boolean(data['sequence-name'] || data['biotype'])}
            isEdit
            otherNamesEdit={
              <div>
                <FormLabel component="legend">Alternative names(s)</FormLabel>
                <div style={{ margin: '0 0.5em' }}>
                  {(data['other-names'] || []).map((otherName) => (
                    <div>
                      <span>{otherName} </span>
                      <Tooltip title="Delete name" placement="right">
                        <IconButton
                          {...getOperationProps(OPERATION_DELETE_NAME_OTHER, {
                            otherName,
                          })}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </Tooltip>
                    </div>
                  ))}
                  <Button
                    {...getOperationProps(OPERATION_ADD_NAMES_OTHER)}
                    variant="raised"
                    size="small"
                  >
                    Add alternative names
                  </Button>
                </div>
              </div>
            }
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
