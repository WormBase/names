import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import { withStyles } from '../../components/elements';
import GeneActivitiesTable from './GeneActivitiesTable';

class RecentActivitiesSingleGene extends Component {
  render() {
    const {classes, activities, authorizedFetch} = this.props;
    return (
      <GeneActivitiesTable
        activities={activities}
        authorizedFetch={authorizedFetch}
        onUpdate={this.fetchData}
        classes={{
          entityCell: classes.entityCell,
          entityColumnHeader: classes.entityColumnHeader,
        }}
      />
    );
  }
}

RecentActivitiesSingleGene.propTypes = {
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func,
  activities: PropTypes.any,
};

const styles = (theme) => ({
  entityColumnHeader: {
    display: 'none',
  },
  entityCell: {
    display: 'none',
  },
});

export default withStyles(styles)(RecentActivitiesSingleGene);
