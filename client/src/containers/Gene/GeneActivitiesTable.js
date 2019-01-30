import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import {
  withStyles,
  Button,
  Humanize,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Timestamp,
  Typography,
} from '../../components/elements';
import ResurrectGeneDialog from './ResurrectGeneDialog';
import UndoMergeGeneDialog from './UndoMergeGeneDialog';
import UndoSplitGeneDialog from './UndoSplitGeneDialog';

const RESURRECT = 'RESURRECT';
const UNDO_MERGE = 'UNDO_MERGE';
const UNDO_SPLIT = 'UNDO_SPLIT';

class GeneActivitiesTable extends Component {
  constructor(props) {
    super(props);
    this.state = {
      showDialog: null,
      selectedActivityIndex: null,
    };
  }

  openDialog = (dialogKey, activityIndex) => {
    this.setState({
      showDialog: dialogKey,
      selectedActivityIndex: activityIndex,
    });
  };

  closeDialog = () => {
    this.setState({
      showDialog: null,
      selectedActivityIndex: null,
    });
  };

  renderActions = ({ activityIndex, ...activityItem }) => {
    const eventType = activityItem['provenance/what'];
    console.log(eventType);
    return (
      <div>
        {
          // eventType === 'event/kill-gene' ?
          //   <Button
          //     onClick={() => this.openDialog(RESURRECT, activityIndex)}
          //     color="primary"
          //   >Undo</Button> :
          //   null
        }
        {eventType === 'event/split-gene' ? (
          <Button
            onClick={() => this.openDialog(UNDO_SPLIT, activityIndex)}
            color="primary"
          >
            Undo
          </Button>
        ) : null}
        {eventType === 'event/merge-genes' ? (
          <Button
            onClick={() => this.openDialog(UNDO_MERGE, activityIndex)}
            color="primary"
          >
            Undo
          </Button>
        ) : null}
      </div>
    );
  };

  getActivityDescriptor = (activityItem = {}, selfGeneId) => {
    const what = activityItem['provenance/what'];
    const { statusChange, relatedGeneId } = (activityItem.changes || []).reduce(
      (result, change) => {
        const { attr, value, added } = change || {};
        if (added)
          if (attr === 'gene/splits' || attr === 'gene/merges') {
            return {
              ...result,
              relatedGeneId: value,
            };
          } else if (attr === 'gene/status') {
            return {
              ...result,
              statusChange: value,
            };
          }
        return result;
      },
      {}
    );

    let eventType;
    if (what === 'event/merge-genes' && !statusChange) {
      eventType = 'merge_from';
    } else if (
      what === 'event/merge-genes' &&
      statusChange === 'gene.status/dead'
    ) {
      eventType = 'merge_into';
    } else if (what === 'event/split-gene' && !statusChange) {
      eventType = 'split_into';
    } else if (
      what === 'event/split-gene' &&
      statusChange === 'gene.status/live'
    ) {
      eventType = 'split_from';
    } else {
      eventType = what;
    }

    const descriptor = {
      eventLabel: eventType || activityItem['provenance/what'],
      entity: {
        'gene/id': selfGeneId,
      },
      relatedEntity: relatedGeneId
        ? {
            'gene/id': relatedGeneId,
          }
        : null,
    };
    return descriptor;
  };

  getGeneIdForEvent = (selectedActivity, eventType) => {
    return (
      selectedActivity &&
      selectedActivity[eventType] &&
      selectedActivity[eventType]['gene/id']
    );
  };

  renderChanges = (changes) => {
    if (!changes || changes.length === 0) {
      return null;
    }

    const changeLookup = changes.reduce((result, changeEntry) => {
      const changeSumamry = {
        ...result[changeEntry.attr],
        [changeEntry.added ? 'added' : 'retracted']: changeEntry.value,
      };
      return {
        ...result,
        [changeEntry.attr]: changeSumamry,
      };
    }, {});
    //return JSON.stringify(changeLookup, null, 2);
    return (
      <Table className={this.props.classes.changeTable}>
        <TableHead>
          <TableRow>
            <TableCell>Attribute changed</TableCell>
            <TableCell>Old value</TableCell>
            <TableCell>
              <strong>New value</strong>
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {Object.keys(changeLookup).map((attr, index) => {
            return (
              <TableRow key={index}>
                <TableCell className={this.props.classes.changeTableCell}>
                  {attr}
                </TableCell>
                <TableCell className={this.props.classes.changeTableCell}>
                  {changeLookup[attr].retracted || '-'}
                </TableCell>
                <TableCell className={this.props.classes.changeTableCell}>
                  <strong>{changeLookup[attr].added || '-'}</strong>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    );
  };

  render() {
    const { classes, activities, onUpdate, selfGeneId } = this.props;
    const { selectedActivityIndex } = this.state;
    const selectedActivity =
      selectedActivityIndex !== null ? activities[selectedActivityIndex] : null;

    return (
      <Paper className={classes.root}>
        <Table>
          <TableBody>
            {this.props.activities.map((activityItem, activityIndex) => {
              const {
                eventLabel,
                entity,
                relatedEntity,
              } = this.getActivityDescriptor(
                activityItem,
                this.props.selfGeneId
              );
              return (
                <TableRow key={activityIndex}>
                  <TableCell className={classes.time}>
                    <p>
                      <Timestamp time={activityItem['provenance/when']} />
                    </p>
                    <em>
                      {activityItem['provenance/who']
                        ? activityItem['provenance/who']['person/name']
                        : 'Unknown'}
                    </em>
                    <span className={classes.via}> via </span>
                    <em>
                      <Humanize>{activityItem['provenance/how']}</Humanize>
                    </em>
                  </TableCell>
                  <TableCell className={classes.entityCell}>
                    {entity ? (
                      <Link to={`/gene/id/${entity['gene/id']}`}>
                        {entity['gene/id']}
                      </Link>
                    ) : null}
                  </TableCell>
                  <TableCell className={classes.eventCell}>
                    <Typography gutterBottom className={classes.eventLabel}>
                      <span>
                        <Humanize>{eventLabel}</Humanize>{' '}
                      </span>
                      {relatedEntity ? (
                        <Link to={`/gene/id/${relatedEntity['gene/id']}`}>
                          {relatedEntity['gene/id']}
                        </Link>
                      ) : null}
                    </Typography>
                    {activityItem['provenance/why'] && (
                      <Typography gutterBottom>
                        <span className={classes.reason}>Reason:</span>{' '}
                        <em>{activityItem['provenance/why']}</em>
                      </Typography>
                    )}
                    {this.renderChanges(activityItem.changes)}
                  </TableCell>
                  {/* <TableCell>
                        {
                          this.renderActions({
                            ...activityItem,
                            activityIndex,
                          })
                        }
                      </TableCell> */}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
        <div>
          <ResurrectGeneDialog
            geneName={selfGeneId}
            wbId={selfGeneId}
            authorizedFetch={this.props.authorizedFetch}
            open={this.state.showDialog === RESURRECT}
            onClose={this.closeDialog}
            onSubmitSuccess={(data) => {
              this.closeDialog();
              onUpdate && onUpdate();
            }}
          />
          <UndoMergeGeneDialog
            geneName={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/merged-into'
            )}
            geneFromName={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/merged-from'
            )}
            wbId={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/merged-into'
            )}
            wbFromId={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/merged-from'
            )}
            authorizedFetch={this.props.authorizedFetch}
            open={this.state.showDialog === UNDO_MERGE}
            onClose={this.closeDialog}
            onSubmitSuccess={(data) => {
              this.closeDialog();
              onUpdate && onUpdate();
            }}
          />
          <UndoSplitGeneDialog
            geneName={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/split-from'
            )}
            geneIntoName={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/split-into'
            )}
            wbId={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/split-from'
            )}
            wbIntoId={this.getGeneIdForEvent(
              selectedActivity,
              'provenance/split-into'
            )}
            authorizedFetch={this.props.authorizedFetch}
            open={this.state.showDialog === UNDO_SPLIT}
            onClose={this.closeDialog}
            onSubmitSuccess={(data) => {
              this.closeDialog();
              onUpdate && onUpdate();
            }}
          />
        </div>
      </Paper>
    );
  }
}

GeneActivitiesTable.propTypes = {
  classes: PropTypes.object.isRequired,
  activities: PropTypes.array,
  onUpdate: PropTypes.func,
  authorizedFetch: PropTypes.func.isRequired,
  selfGeneId: PropTypes.string,
};

GeneActivitiesTable.defaultProps = {
  activities: [],
};

const styles = (theme) => ({
  time: {
    whiteSpace: 'nowrap',
  },
  root: {
    overflow: 'scroll',
  },
  entityColumnHeader: {},
  entityCell: {},
  eventCell: {},
  eventLabel: {
    textTransform: 'capitalize',
    // fontStyle: 'italic',
  },
  changeTable: {
    backgroundColor: theme.palette.background.default,
  },
  via: {
    color: theme.palette.text.secondary,
  },
  reason: {
    color: theme.palette.text.secondary,
    fontStyle: 'normal',
  },
  changeTableCell: {
    border: 'none',
  },
});

export default withStyles(styles)(GeneActivitiesTable);
