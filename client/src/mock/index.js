var fetchMock = require('fetch-mock');

export function startMock() {
  // Mock the fetch() global to always return the same value for GET
  // requests to all URLs.
  fetchMock.get('*', {hello: 'world'});
}


export function stopMock() {
  // Unmock.
  fetchMock.restore();
}
