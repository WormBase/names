import React from 'react';
import PropTypes from 'prop-types';
import { EntityDirectory } from '../Entity';
// import RecentActivities from './RecentActivities';

class GeneDirectory extends React.Component {
  // renderHistory = () => {
  //   const { entityType } = this.props;
  //   return <RecentActivities entityType={entityType} />;
  // };
  render() {
    const { entityType } = this.props;
    return (
      <EntityDirectory
        entityType={entityType}
        renderHistory={this.renderHistory}
      />
    );
  }
}

GeneDirectory.propTypes = {
  entityType: PropTypes.string.isRequired,
};

export default GeneDirectory;
