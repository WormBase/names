var fetchMock = require('fetch-mock');

var ready = true;
export function mockFetchOrNot(mockCallback, nativeCallback, shouldMock) {
  console.log('ready ' + ready);
  if (ready) {
    ready = false;
    if (shouldMock) {
      fetchMock.restore();
      mockCallback(fetchMock);
      return Promise.resolve(nativeCallback()).then((result) => {
        console.log(`While mock: ready = ${ready}`);
        fetchMock.restore();
        ready = true;
        console.log(`After mock: set ready to ${ready}`);
        console.log(result);
        return result;
      });
    } else {
      return Promise.resolve(nativeCallback()).then((result) => {
        console.log(`While native: ready = ${ready}`);
        ready = true;
        console.log(`After native: set ready to ${ready}`);
        console.log(result);
        return result;
      });
    }
  } else {
    console.log(`mock not ready`);
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve(mockFetchOrNot(mockCallback, nativeCallback, shouldMock));
      }, 300);
    })
  }

}
