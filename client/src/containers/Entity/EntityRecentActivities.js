import React, { useCallback } from 'react';
import moment from 'moment';
import { useDataFetch } from '../Authenticate/AuthorizationContext';
import EntityHistory from './EntityHistory';
import { NoData, CircularProgress } from '../../components/elements';

function formatTime(timestamp) {
  return moment(timestamp).calendar(null, {
    sameElse: 'LLL',
  });
}

function EntityRecentActivities(props) {
  const { entityType } = props;
  const memoizedFetchFunc = useCallback(
    () => (authorizedFetch) =>
      authorizedFetch(`/api/recent/${entityType}`, {
        method: 'GET',
      }),
    [entityType]
  );

  const { data, isLoading } = useDataFetch(memoizedFetchFunc, {});
  const { activities = [], from, until } = data;
  return isLoading ? (
    <CircularProgress />
  ) : activities.length ? (
    <EntityHistory activities={activities} entityType={entityType} />
  ) : (
    <NoData>
      No activities between <strong>{formatTime(from)}</strong> and{' '}
      <strong>{formatTime(until)}</strong>
    </NoData>
  );
}

export default EntityRecentActivities;
