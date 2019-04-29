import React, { useContext, useState, useEffect } from 'react';
import { AuthorizationContext } from '../Authenticate';
import EntityHistory from './EntityHistory';
import { Paper } from '../../components/elements';

function EntityRecentActivities(props) {
  const { entityType } = props;
  const { authorizedFetch } = useContext(AuthorizationContext);
  const [activities, setActivities] = useState([]);
  const [requestStatus, setRequestStatus] = useState('REQUEST_BEGIN');
  useEffect(
    () => {
      if (!authorizedFetch) {
        return;
      }
      authorizedFetch(`/api/recent/${entityType}`, {
        method: 'GET',
      })
        .then((response) => {
          return Promise.all([response, response.json()]);
        })
        .then(([response, data]) => {
          if (response.ok) {
            setRequestStatus('REQUEST_SUCCESS');
            setActivities(data.activities);
          } else {
            setRequestStatus('REQUEST_FAILURE');
          }
        })
        .catch(() => {
          // error handling
        });
      return () => {};
    },
    [entityType]
  );
  return <EntityHistory activities={activities} entityType={entityType} />;
}

export default EntityRecentActivities;
