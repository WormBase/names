export default function makeRequest() {
  return fetch("http://httpbin.org/get").then(function(response) {
    return response.json();
  });
};
