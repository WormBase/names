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
          {
            entity: {
              id: 'WB333',
              label: 'aaa-22',
            },
            relatedEntity: {
              id: 'WB345',
              label: 'aaa-222'
            },
            eventType: 'merge',
            curatedBy: {
              name: 'Gary'
            },
            time: '2015-08-19T23:15:30.000Z',
            agent: 'script',
            reason: 'Don\'t like it',
          },
          {
            entity: {
              id: 'WB4',
              label: 'aaa-3',
            },
            relatedEntity: null,
            eventType: 'kill',
            curatedBy: {
              name: 'Gary'
            },
            time: '2015-08-19T23:15:30.000Z',
            agent: 'script',
            reason: 'Don\'t like it',
          },
          {
            entity: {
              id: 'WB1',
              label: 'ab',
            },
            relatedEntity: {
              id: 'WB345',
              label: 'abc-1'
            },
            eventType: 'split',
            curatedBy: {
              name: 'Gary'
            },
            time: '2015-08-19T23:15:30.000Z',
            agent: 'script',
            reason: 'Don\'t like it',
          },
          {
            entity: {
              id: 'WB1',
              label: 'ab',
            },
            relatedEntity: null,
            eventType: 'update',
            curatedBy: {
              name: 'Michael'
            },
            time: '2015-07-20T23:15:30.000Z',
            agent: 'web form',
            reason: 'Looked funny',
          },
          {
            entity: {
              id: 'WB1',
              label: 'ab',
            },
            relatedEntity: null,
            eventType: 'create',
            curatedBy: {
              name: 'Michael'
            },
            time: '2014-01-10T23:15:30.000Z',
            agent: 'script',
            reason: 'New',
          },
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
    const {classes, authorizedFetch} = this.props;
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
