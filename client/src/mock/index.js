var fetchMock = require('fetch-mock');

export function mockFetchOrNot(mockCallback, nativeCallback, shouldMock) {
  if (shouldMock) {
    fetchMock.restore();
    mockCallback(fetchMock);
    return Promise.resolve(nativeCallback()).then((result) => {
      fetchMock.restore();
      return result;
    });
  } else {
    return nativeCallback();
  }
}
