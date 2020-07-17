export function pastTense(eventText = '') {
  return eventText.replace(
    /(creat|updat|merg|kill|resurrect|suppress)e?/gi,
    '$1ed'
  );
}

export function getActivityDescriptor(activityItem = {}, selfGeneId) {
  const what = activityItem['what'];
  const { statusChange, relatedGeneId } = (activityItem.changes || []).reduce(
    (result, change) => {
      const { attr, value, added } = change || {};
      if (added)
        if (attr === 'splits' || attr === 'merges') {
          return {
            ...result,
            relatedGeneId: value,
          };
        } else if (attr === 'status') {
          return {
            ...result,
            statusChange: value,
          };
        }
      return result;
    },
    {}
  );

  let eventType;
  if (what === 'merge-genes' && !statusChange) {
    eventType = 'merge_into';
  } else if (what === 'merge-genes' && statusChange === 'dead') {
    eventType = 'merge_from';
  } else if (what === 'split-gene' && !statusChange) {
    eventType = 'split_into';
  } else if (what === 'split-gene' && statusChange === 'live') {
    eventType = 'split_from';
  } else if (what.match(/new-.+/)) {
    eventType = 'create';
  } else {
    eventType = what.replace(/-\w+$/, '').trim();
    console.log(eventType);
  }

  const descriptor = {
    eventLabel: eventType || activityItem['what'],
    entity: {
      id: selfGeneId || activityItem.id,
    },
    relatedEntity: relatedGeneId
      ? {
          id: relatedGeneId,
        }
      : null,
  };
  return descriptor;
}
