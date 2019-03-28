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
import { pastTense, getActivityDescriptor } from '../../utils/events';

class EntityHistory extends Component {
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
    const { classes, activities = [], wbId, entityType } = this.props;
    return (
      <Paper className={classes.root}>
        <Table>
          <TableBody>
            {this.props.activities.map((activityItem, activityIndex) => {
              const {
                eventLabel,
                entity,
                relatedEntity,
              } = getActivityDescriptor(activityItem, wbId);
              console.log(relatedEntity);
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
                  <TableCell className={classes.eventCell}>
                    <Typography gutterBottom className={classes.eventLabel}>
                      <span>
                        <Humanize postProcessor={pastTense}>
                          {eventLabel}
                        </Humanize>{' '}
                      </span>
                      {relatedEntity ? (
                        <Link
                          to={`/${entityType}/id/${
                            relatedEntity[`${entityType}/id`]
                          }`}
                        >
                          {relatedEntity[`${entityType}/id`]}
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
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Paper>
    );
  }
}

EntityHistory.propTypes = {
  classes: PropTypes.object.isRequired,
  wbId: PropTypes.string.isRequired,
  entityType: PropTypes.string.isRequired,
  activities: PropTypes.array,
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

export default withStyles(styles)(EntityHistory);
