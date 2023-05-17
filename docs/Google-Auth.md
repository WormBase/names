# Client credentials
All members of the WormBase organisation should have a Google account,
and thus the names service uses Google-Auth as the mechanism for authentication.

Log in to the name service website using your wormbase google email (ending on `@wormbase.org`).


# API Authorization token
As authorization mechanism, the names service requires that either a temporary Google Auth Code
(for exchange using the identity endpoint) or a valid id_token is passed in through the HTTP(S) request Authorization header
sent to all API endpoints.
To obtain a valid ID-token for calling the API, log in to the name service website with your personal account,
browse to your profile page (`/me`) and copy the ID-token shown (by clicking the `copy to clipboard` button).

The token should then be passed in the header as described below.
For example; given a suitable JSON file for the payload,
the _curl_ command below creates a number of genes via the names service batch API:

```bash
curl -H "Content-type: application/json" \
     -H "Authorization: Token $ID_TOKEN" \
     --data @payload.json \
     https://names.wormbase.org/api/batch/gene
```

## Google Oath 2.0 Clients
The Google Oath 2.0 clients used by the Name service can be accessed through
the [credentials section of the Google Developers Console][1].
The Name Service uses two Google Oath 2.0 Client applications for different environments:
 * Web - Dev
 * Web - Prod

For local testing and development, use the application credentials of the `Web - Dev` application.
For applications deployed to the stage or production environment, use the `Web - Prod` application credentials.

Click on the respective application name in the console to access the details page showing the Client ID and Client secret
to be used by the Name Service to enable Google Authentication.

[1]: https://console.developers.google.com/apis/credentials?project=wormbase-names-service