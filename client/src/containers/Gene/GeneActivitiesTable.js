import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
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

  openResurrectGeneDialog = (activityIndex) => {
    this.setState({
      showDialog: RESURRECT,
      selectedActivityIndex: activityIndex,
    });
  }

  closeDialog = () => {
    this.setState({
      showDialog: null,
      selectedActivityIndex: null,
    });
  }

  renderActions = ({eventType, entity, relatedEntity, activityIndex}) => {
    return (
      <div>
        {
          eventType === 'kill' ?
            <Button
              onClick={() => this.openResurrectGeneDialog(activityIndex)}
              color="primary"
            >Resurrect</Button> :
            null
        }
      </div>
    )
  }

  render() {
    const {classes, activities, onUpdate} = this.props;
    const {selectedActivityIndex} = this.state;
    const selectedActivity = selectedActivityIndex !== null ? activities[selectedActivityIndex] : null;

    return (
      <div>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Time</TableCell>
              <TableCell>Event type</TableCell>
              <TableCell className={classes.entityColumnHeader}>Entity</TableCell>
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
                  return (
                    <TableRow>
                      <TableCell className={classes.time}>
                        <Timestamp time={activityItem.time}/>
                      </TableCell>
                      <TableCell>{activityItem.eventType}</TableCell>
                      <TableCell className={classes.entityCell}>
                        {
                          activityItem.entity ?
                            <Link to={`/gene/id/${activityItem.entity.id}`}>{activityItem.entity.label}</Link> :
                            null
                        }
                      </TableCell>
                      <TableCell>
                        {
                          activityItem.relatedEntity ?
                            <Link to={`/gene/id/${activityItem.relatedEntity.id}`}>{activityItem.relatedEntity.label}</Link> :
                            null
                        }
                      </TableCell>
                      <TableCell>{activityItem.curatedBy.name}</TableCell>
                      <TableCell>{activityItem.reason}</TableCell>
                      <TableCell>{activityItem.agent}</TableCell>
                      <TableCell>{this.renderActions({
                        ...activityItem,
                        activityIndex,
                      })}</TableCell>
                    </TableRow>
                  )
                }
              )
            }
          </TableBody>
        </Table>
        <div>
          <ResurrectGeneDialog
            geneName={selectedActivity && selectedActivity.entity.label}
            wbId={selectedActivity && selectedActivity.entity.id}
            authorizedFetch={this.props.authorizedFetch}
            open={this.state.showDialog === RESURRECT}
            onClose={this.closeDialog}
            onSubmitSuccess={(data) => {
              this.closeResurrectGeneDialog();
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
};

GeneActivitiesTable.defaultProps = {
  GeneActivitiesTable: [],
};

const styles = (theme) => ({
  time: {
    whiteSpace: 'nowrap',
  },
  entityColumnHeader: {},
  entityCell: {},
});

export default withStyles(styles)(GeneActivitiesTable);
