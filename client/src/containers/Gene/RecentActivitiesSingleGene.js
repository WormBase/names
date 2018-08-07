import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import { withStyles } from '../../components/elements';
import GeneActivitiesTable from './GeneActivitiesTable';

class RecentActivitiesSingleGene extends Component {
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
            relatedEntity: null,
            eventType: 'create',
            curatedBy: {
              name: 'Michael'
            },
            time: '2014-01-10T23:15:30.000Z',
            agent: 'script',
            reason: 'New',
          },
        ].map((activityItem) => ({
          ...activityItem,
          entity: {
            id: this.props.wbId,
            label: this.props.wbId,
          },
        }))
        return mockFetch.get('*', mockData);
      },
      () => {
        return fetch(`/api/gene/recent/${this.props.wbId}`, {
          method: 'GET'
        });
      },
      true,
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
