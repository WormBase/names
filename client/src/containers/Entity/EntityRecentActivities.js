import React, { useContext, useCallback } from 'react';
import moment from 'moment';
import AuthorizationContext, {
  useDataFetch,
} from '../Authenticate/AuthorizationContext';
import EntityHistory from './EntityHistory';
import ActivitiesCopy from './ActivitiesCopy';
import { NoData, CircularProgress } from '../../components/elements';

function formatTime(timestamp) {
  return moment(timestamp).calendar(null, {
    sameElse: 'LLL',
  });
}

function EntityRecentActivities(props) {
  const { entityType } = props;
  const { authorizedFetch } = useContext(AuthorizationContext);
  const memoizedFetchFunc = useCallback(
    () => () =>
      authorizedFetch(`/api/recent/${entityType}`, {
        method: 'GET',
      }),
    [entityType, authorizedFetch]
  );

  const { data, isLoading } = useDataFetch(memoizedFetchFunc, {});
  const { activities = [], from, until } = data;
  return isLoading ? (
    <CircularProgress />
  ) : activities.length ? (
    <div>
      <ActivitiesCopy activities={activities} entityType={entityType}>
        zzzz
      </ActivitiesCopy>
      <EntityHistory activities={activities} entityType={entityType} />
    </div>
  ) : (
    <NoData>
      No activities between <strong>{formatTime(from)}</strong> and{' '}
      <strong>{formatTime(until)}</strong>
    </NoData>
  );
}

export default EntityRecentActivities;
