# Client credentials
All members of the WormBase organisation should have a Google account,
and thus the names service uses Google-Auth as the mechanism for authentication.

The CLIENT_ID and CLIENT_SECRET for the names service applications can be obtained from
the [credentials section of the Google Developers Console][1].

There are two applications:

 * Web
 * Console

For use with scripts, you'll want the credentials for the Console application.
The Web application is credentials are used by the names web service. 


# Client token
The names service requires that the a valid id_token is passed in a HTTP(S) Authorization header.
To obtain a token for use with the API, you can use the example script as follows:

```bash
CLIENT_ID=<copy console app client_id from google dev console)>
CLIENT_SECRET=<copy console app client_secret from google dev console)>
TOKEN=$(./obtaintoken.sh "$CLIENT_ID" "$CLIENT_SECRET")
```

The token is then passed in the header as described above.
For example; given a suitable JSON file for the payload,
the _curl_ command below creates a number of genes via the names service batch API:

```bash
curl -H "Content-type: application/json" \
     -H "Authorization: Token $TOKEN" \
     --data @payload.json \
     https://names.wormbase.org/api/batch/gene
```


[1]: https://console.developers.google.com/apis/credentials?project=wormbase-names-service