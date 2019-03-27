import React from 'react';
import PropTypes from 'prop-types';
import { EntityDirectory } from '../../components/elements';
import RecentActivities from './RecentActivities';

class GeneDirectory extends React.Component {
  renderHistory = () => {
    return <RecentActivities />;
  };
  render() {
    return (
      <EntityDirectory entityType="gene" renderHistory={this.renderHistory} />
    );
  }
}

GeneDirectory.propTypes = {};

export default GeneDirectory;
