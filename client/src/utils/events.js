export function pastTense(eventText = '') {
  return eventText.replace(/(merg|kill|resurrect|suppress)e?/g, '$1ed');
}

export function getActivityDescriptor(activityItem = {}, selfGeneId) {
  const what = activityItem['provenance/what'];
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
  } else {
    eventType = what.replace(/-gene/, '').trim();
    console.log(eventType);
  }

  const descriptor = {
    eventLabel: eventType || activityItem['provenance/what'],
    entity: {
      'gene/id': selfGeneId,
    },
    relatedEntity: relatedGeneId
      ? {
          'gene/id': relatedGeneId,
        }
      : null,
  };
  return descriptor;
}
