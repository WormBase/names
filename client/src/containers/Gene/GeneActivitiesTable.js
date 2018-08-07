import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import {
  withStyles,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Timestamp,
} from '../../components/elements';

class GeneActivitiesTable extends Component {
  render() {
    const {classes} = this.props;
    return (
      <Table classes={{root: classes.root}}>
        <TableHead>
          <TableRow>
            <TableCell>Time</TableCell>
            <TableCell>Event type</TableCell>
            <TableCell className={classes.entityColumnHeader}>Entity</TableCell>
            <TableCell>Related entity</TableCell>
            <TableCell>Curated by</TableCell>
            <TableCell>Reason</TableCell>
            <TableCell>Agent</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {
            this.props.activities.map(
              (historyItem) => {
                return (
                  <TableRow>
                    <TableCell className={classes.time}>
                      <Timestamp time={historyItem.time}/>
                    </TableCell>
                    <TableCell>{historyItem.eventType}</TableCell>
                    <TableCell className={classes.entityCell}>
                      {
                        historyItem.entity ?
                          <Link to={`/gene/id/${historyItem.entity.id}`}>{historyItem.entity.label}</Link> :
                          null
                      }
                    </TableCell>
                    <TableCell>
                      {
                        historyItem.relatedEntity ?
                          <Link to={`/gene/id/${historyItem.relatedEntity.id}`}>{historyItem.relatedEntity.label}</Link> :
                          null
                      }
                    </TableCell>
                    <TableCell>{historyItem.curatedBy.name}</TableCell>
                    <TableCell>{historyItem.reason}</TableCell>
                    <TableCell>{historyItem.agent}</TableCell>
                  </TableRow>
                )
              }
            )
          }
        </TableBody>
      </Table>
    );
  }
}

GeneActivitiesTable.propTypes = {
  classes: PropTypes.object.isRequired,
  activities: PropTypes.array,
};

GeneActivitiesTable.defaultProps = {
  GeneActivitiesTable: [],
};

const styles = (theme) => ({
  root: {
    // width: 'initial',
  },
  time: {
    whiteSpace: 'nowrap',
  },
  entityColumnHeader: {},
  entityCell: {},
});

export default withStyles(styles)(GeneActivitiesTable);
