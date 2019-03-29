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
        const mockData = [];
        return mockFetch.get('*', mockData);
      },
      () => {
        return fetch(`/api/recent/gene`, {
          method: 'GET',
        });
      },
      true
    )
      .then((response) => response.json())
      .then((data) => {
        this.setState({
          data: data.reason ? [] : data,
          loading: false,
        });
      })
      .catch((e) => console.log('error', e));
  };

  render() {
    return (
      <div>
        <GeneActivitiesTable
          activities={this.state.data}
          onUpdate={this.fetchData}
        />
        <p>
          <em
            style={{
              color: '#999',
            }}
          >
            Coming soon!
          </em>
        </p>
      </div>
    );
  }
}

RecentActivities.propTypes = {
  entityType: PropTypes.string.isRequired,
};

export default RecentActivities;
