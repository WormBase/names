import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import GeneActivitiesTable from './GeneActivitiesTable';

class RecentActivities extends Component {
  constructor(props) {
    super(props);
    this.state = {
      data: [],
    };
  }

  componentDidMount() {
    this.fetchData();
  }

  fetchData = () => {
    mockFetchOrNot(
      (mockFetch) => {
        const mockData = [
        ];
        return mockFetch.get('*', mockData);
      },
      () => {
        return fetch(`/api/recent/gene`, {
          method: 'GET'
        });
      },
      true
    ).then((response) => response.json()).then((data) => {
      this.setState({
        data: data.reason ? [] : data,
        loading: false,
      });
    }).catch((e) => console.log('error', e));
  }

  render() {
    const {authorizedFetch} = this.props;
    return (
      <GeneActivitiesTable
        activities={this.state.data}
        authorizedFetch={authorizedFetch}
        onUpdate={this.fetchData}
      />
    );
  }
}

RecentActivities.propTypes = {
  wbId: PropTypes.string.isRequired,
  authorizedFetch: PropTypes.func,
};

export default RecentActivities;
