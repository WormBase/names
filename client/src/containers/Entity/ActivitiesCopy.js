import React from 'react';
import PropTypes from 'prop-types';
import copy from 'copy-to-clipboard';
import moment from 'moment';
import { Button } from '../../components/elements';

function ActivitiesCopy({ entityType, activities, children }) {
  const generate = () => {
    const [activitiesToday] = [...activities].reduce(
      ([activitiesAccumulator, isTodayAccumulator], activity) => {
        if (isTodayAccumulator) {
          if (moment(activity['provenance/when']).isSame(moment(), 'day')) {
            activitiesAccumulator.push(activity);
            return [activitiesAccumulator, true];
          } else {
            return [activitiesAccumulator, false];
          }
        } else {
          return [activitiesAccumulator, false];
        }
      },
      [[], true]
    );
    return activitiesToday
      .filter((activity) =>
        activity['provenance/what'].match(/^event\/(new|update)-.+/)
      )
      .map((activity) => {
        const { changes } = activity;
        const id = activity[`${entityType}/id`];

        const [{ value: name }] = changes.filter(
          ({ attr, added }) => added && attr.match(/.+\/(cgc-)?name/)
        );
        return `${name}\t${id}`;
      })
      .join('\n');
  };
  const onClick = () => {
    copy(generate());
  };
  return <Button onClick={onClick}>{children}</Button>;
}

ActivitiesCopy.propTypes = {
  children: PropTypes.element,
  entityType: PropTypes.string.isRequired,
  activities: PropTypes.arrayOf(
    PropTypes.shape({
      'provenance/what': PropTypes.string.isRequired,
      changes: PropTypes.arrayOf(
        PropTypes.shape({
          attr: PropTypes.string.isRequired,
          value: PropTypes.string.isRequired,
          added: PropTypes.bool.isRequired,
        })
      ),
    })
  ),
};

export default ActivitiesCopy;
