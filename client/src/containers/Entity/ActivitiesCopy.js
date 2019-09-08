import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
// import copy from 'copy-to-clipboard';
import moment from 'moment';
import { Button, useClipboard } from '../../components/elements';

function ActivitiesCopy({ entityType, activities }) {
  const entriesToday = useMemo(
    () => {
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
      return activitiesToday.filter((activity) =>
        activity['provenance/what'].match(/^event\/(new|update)-.+/)
      );
    },
    [activities]
  );

  const formatedEntriesToday = useMemo(
    () => {
      return entriesToday
        .map((activity) => {
          const { changes } = activity;
          const id = activity[`${entityType}/id`];

          const [{ value: name }] = changes.filter(
            ({ attr, added }) => added && attr.match(/.+\/(cgc-)?name/)
          );
          return `${name} ${id}`;
        })
        .join('\n');
    },
    [entriesToday, entityType]
  );

  const { copied, handleCopy } = useClipboard(formatedEntriesToday);

  return (
    <React.Fragment>
      <p>Need the {entityType} IDs in the Ontology Annotator (OA)?</p>
      <p>
        <Button
          variant="raised"
          color="primary"
          disabled={entriesToday.length === 0}
          onClick={handleCopy}
        >
          {copied ? 'Copied' : 'Copy'}
        </Button>{' '}
        the <strong>{entriesToday.length}</strong> name-ID pairs registered
        today.
      </p>
    </React.Fragment>
  );
}

ActivitiesCopy.propTypes = {
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
