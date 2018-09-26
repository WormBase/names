import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import {
  withStyles,
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Timestamp,
} from '../../components/elements';
import ResurrectGeneDialog from './ResurrectGeneDialog';
import UndoMergeGeneDialog from './UndoMergeGeneDialog';
import UndoSplitGeneDialog from './UndoSplitGeneDialog';
import { Humanize } from '../../components/elements';

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
  }

  closeDialog = () => {
    this.setState({
      showDialog: null,
      selectedActivityIndex: null,
    });
  }

  renderActions = ({activityIndex, ...activityItem}) => {
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
        {
          eventType === 'event/split-gene' ?
            <Button
              onClick={() => this.openDialog(UNDO_SPLIT, activityIndex)}
              color="primary"
            >Undo</Button> :
            null
        }
        {
          eventType === 'event/merge-genes' ?
            <Button
              onClick={() => this.openDialog(UNDO_MERGE, activityIndex)}
              color="primary"
            >Undo</Button> :
            null
        }
      </div>
    )
  }

  getActivityDescriptor = (activityItem, selfGeneId) => {
    const entityKeys = [
      'provenance/merged-from',
      'provenance/merged-into',
      'provenance/split-from',
      'provenance/split-into',
    ];
    const selfEntityStub = {
      'gene/id': selfGeneId,
    };
    const [entityKey] = entityKeys.filter((key) => (
      activityItem[key] && activityItem[key]['gene/id'] === selfGeneId
    ));
    const [relatedEntityKey] = entityKeys.filter((key) => (
      activityItem[key] && activityItem[key]['gene/id'] !== selfGeneId
    ));

    let eventType;
    if (entityKey) {
      if (activityItem['provenance/merged-into'] === activityItem[entityKey]) {
        eventType = 'merge_from';
      } else if (activityItem['provenance/merged-from'] === activityItem[entityKey]) {
        eventType = 'merge_into';
      } else if (activityItem['provenance/split-into'] === activityItem[entityKey]) {
        eventType = 'split_from';
      } else if (activityItem['provenance/split-from'] === activityItem[entityKey]) {
        eventType = 'split_into';
      }
    } else {
      eventType = activityItem['provenance/what'];
    }

    return [
      eventType,
      activityItem[entityKey] || selfEntityStub,
      activityItem[relatedEntityKey]
    ];
  }

  getGeneIdForEvent = (selectedActivity, eventType) => {
    return selectedActivity &&  selectedActivity[eventType] && selectedActivity[eventType]["gene/id"];
  }

  renderChanges = (changes) => {
    const changeLookup = changes.reduce((result, changeEntry) => {
      const changeSumamry = {
        ...result[changeEntry.attr],
        [changeEntry.added ? 'added' : 'retracted']: changeEntry.value,
      };
      return ({
        ...result,
        [changeEntry.attr]: changeSumamry,
      })
    }, {});
    //return JSON.stringify(changeLookup, null, 2);
    return (
      <Table className={this.props.classes.changeTable}>
        <TableHead>
          <TableRow>
            <TableCell>Attribute changed</TableCell>
            <TableCell>Old value</TableCell>
            <TableCell><strong>New value</strong></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {
            Object.keys(changeLookup).map((attr) => {
              return (
                <TableRow>
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
              )
            })
          }
        </TableBody>
      </Table>
    );
  }

  render() {
    const {classes, activities, onUpdate, selfGeneId} = this.props;
    const {selectedActivityIndex} = this.state;
    const selectedActivity = selectedActivityIndex !== null ? activities[selectedActivityIndex] : null;

    return (
      <div>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Time</TableCell>
              <TableCell className={classes.entityColumnHeader}>Entity</TableCell>
              <TableCell>Event type</TableCell>
              <TableCell>Related entity</TableCell>
              <TableCell>Curated by</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell>Agent</TableCell>
              <TableCell></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {
              this.props.activities.map(
                (activityItem, activityIndex) => {
                  const [eventLabel, entity, relatedEntity] = this.getActivityDescriptor(activityItem, this.props.selfGeneId);
                  return (
                    <TableRow key={activityIndex}>
                      <TableCell className={classes.time}>
                        <Timestamp time={activityItem['provenance/when']}/>
                      </TableCell>
                      <TableCell className={classes.entityCell}>
                        {
                          entity ?
                            <Link to={`/gene/id/${entity['gene/id']}`}>{entity['gene/id']}</Link> :
                            null
                        }
                      </TableCell>
                      <TableCell className={classes.eventCell}>
                        <span className={classes.eventLabel}><Humanize>{eventLabel}</Humanize></span>
                        {this.renderChanges(activityItem.changes)}
                      </TableCell>
                      <TableCell>
                        {
                          relatedEntity ?
                            <Link to={`/gene/id/${relatedEntity['gene/id']}`}>{relatedEntity['gene/id']}</Link> :
                            null
                        }
                      </TableCell>
                      <TableCell>{activityItem['provenance/who']['person/name']}</TableCell>
                      <TableCell>{activityItem['provenance/why']}</TableCell>
                      <TableCell>
                        <Humanize>
                          {activityItem['provenance/how']}
                        </Humanize>
                      </TableCell>
                      <TableCell>
                        {
                          // this.renderActions({
                          //   ...activityItem,
                          //   activityIndex,
                          // })
                        }
                      </TableCell>
                    </TableRow>
                  )
                }
              )
            }
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
            geneName={this.getGeneIdForEvent(selectedActivity, 'provenance/merged-into')}
            geneFromName={this.getGeneIdForEvent(selectedActivity, 'provenance/merged-from')}
            wbId={this.getGeneIdForEvent(selectedActivity, 'provenance/merged-into')}
            wbFromId={this.getGeneIdForEvent(selectedActivity, 'provenance/merged-from')}
            authorizedFetch={this.props.authorizedFetch}
            open={this.state.showDialog === UNDO_MERGE}
            onClose={this.closeDialog}
            onSubmitSuccess={(data) => {
              this.closeDialog();
              onUpdate && onUpdate();
            }}
          />
          <UndoSplitGeneDialog
            geneName={this.getGeneIdForEvent(selectedActivity, 'provenance/split-from')}
            geneIntoName={this.getGeneIdForEvent(selectedActivity, 'provenance/split-into')}
            wbId={this.getGeneIdForEvent(selectedActivity, 'provenance/split-from')}
            wbIntoId={this.getGeneIdForEvent(selectedActivity, 'provenance/split-into')}
            authorizedFetch={this.props.authorizedFetch}
            open={this.state.showDialog === UNDO_SPLIT}
            onClose={this.closeDialog}
            onSubmitSuccess={(data) => {
              this.closeDialog();
              onUpdate && onUpdate();
            }}
          />
        </div>
      </div>
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
  entityColumnHeader: {},
  entityCell: {},
  eventCell: {
  },
  eventLabel: {
    lineHeight: '1.5em',
    fontStyle: 'italic',
  },
  changeTable: {
    backgroundColor: theme.palette.background.default,
  },
  changeTableCell: {
    border: 'none',
  },
});

export default withStyles(styles)(GeneActivitiesTable);
