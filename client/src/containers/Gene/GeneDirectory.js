import React from 'react';
import PropTypes from 'prop-types';
import { EntityDirectory } from '../../components/elements';
import RecentActivities from './RecentActivities';

class GeneDirectory extends React.Component {
  renderHistory = () => {
    const { authorizedFetch } = this.props;
    return <RecentActivities authorizedFetch={authorizedFetch} />;
  };
  render() {
    return (
      <EntityDirectory entityType="gene" renderHistory={this.renderHistory} />
    );
  }
}

GeneDirectory.propTypes = {
  authorizedFetch: PropTypes.any,
};

export default GeneDirectory;
