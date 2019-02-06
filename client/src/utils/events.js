export function pastTense(eventText = '') {
  return eventText.replace(/(merg|kill|resurrect|suppress)e?/g, '$1ed');
}
