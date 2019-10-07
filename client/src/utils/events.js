export function pastTense(eventText = '') {
  return eventText.replace(/(creat|merg|kill|resurrect|suppress)e?/g, '$1ed');
}

function activityEntityType(activityItem = {}) {
  return Object.keys(activityItem).reduce((result, key) => {
    if (result) {
      return result;
    }
    const match = key.match(/(.+)\/id/);
    if (match) {
      return match[1];
    } else {
      return null;
    }
  }, null);
}

export function getActivityDescriptor(activityItem = {}, selfGeneId) {
  const what = activityItem['what'];
  const entityType = activityEntityType(activityItem);
  const { statusChange, relatedGeneId } = (activityItem.changes || []).reduce(
    (result, change) => {
      const { attr, value, added } = change || {};
      if (added)
        if (attr === 'gene/splits' || attr === 'gene/merges') {
          return {
            ...result,
            relatedGeneId: value,
          };
        } else if (attr === 'gene/status') {
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
  if (what === 'event/merge-genes' && !statusChange) {
    eventType = 'merge_from';
  } else if (
    what === 'event/merge-genes' &&
    statusChange === 'gene.status/dead'
  ) {
    eventType = 'merge_into';
  } else if (what === 'event/split-gene' && !statusChange) {
    eventType = 'split_into';
  } else if (
    what === 'event/split-gene' &&
    statusChange === 'gene.status/live'
  ) {
    eventType = 'split_from';
  } else if (what.match(/new-.+/)) {
    eventType = 'create';
  } else {
    eventType = what.replace(/-\w+$/, '').trim();
    console.log(eventType);
  }

  const descriptor = {
    eventLabel: eventType || activityItem['provenance/what'],
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
