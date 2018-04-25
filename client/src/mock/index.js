var fetchMock = require('fetch-mock');

export function startMock() {
  // Mock the fetch() global to always return the same value for GET
  // requests to all URLs.

  fetchMock.get("/api/gene", {
    id: 'WB1',
    cgcName: 'ab',
    sequenceName: 'AB',
    species: 'Caenorhabditis elegans',
    biotype: 'cds',
  });

  fetchMock.get('*', {hello: 'world'});  
}


export function stopMock() {
  // Unmock.
  fetchMock.restore();
}
