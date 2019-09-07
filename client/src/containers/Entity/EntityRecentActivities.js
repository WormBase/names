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
  const { activities: activitiesRaw = [], from, until } = data;
  const activities = [...activitiesRaw].reverse();
  return isLoading ? (
    <CircularProgress />
  ) : activities.length ? (
    <div>
      <p>
        Need to enter {entityType} IDs into OA? Copy the name-ID pairs{' '}
        <ActivitiesCopy activities={activities} entityType={entityType}>
          here
        </ActivitiesCopy>
      </p>
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
