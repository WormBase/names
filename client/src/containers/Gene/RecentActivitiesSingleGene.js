import React, { Component } from 'react';
import { mockFetchOrNot } from '../../mock';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import {
  withStyles,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Timestamp,
} from '../../components/elements';
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
        ];
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
    const {classes} = this.props;
    return (
      <GeneActivitiesTable
        activities={this.state.data}
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
};

const styles = (theme) => ({
  root: {
    // width: 'initial',
  },
  time: {
    whiteSpace: 'nowrap',
  },
  entityColumnHeader: {
    display: 'none',
  },
  entityCell: {
    display: 'none',
  },
});

export default withStyles(styles)(RecentActivitiesSingleGene);
