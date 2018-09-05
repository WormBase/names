import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '../../components/elements';
import GeneActivitiesTable from './GeneActivitiesTable';

class RecentActivitiesSingleGene extends Component {
  render() {
    const {classes, wbId, ...otherProps} = this.props;
    return (
      <GeneActivitiesTable
        classes={{
          entityCell: classes.entityCell,
          entityColumnHeader: classes.entityColumnHeader,
        }}
        selfGeneId={wbId}
        {...otherProps}
      />
    );
  }
}

RecentActivitiesSingleGene.propTypes = {
  wbId: PropTypes.string.isRequired,
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
