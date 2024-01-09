import React, { useContext, useReducer, useEffect } from 'react';
import { CopyToClipboard } from 'react-copy-to-clipboard';

import { Button } from '../../components/elements';
import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';
import { useCallback } from 'react';

const ACTION_STORE = 'STORE';
const ACTION_REVOKE = 'REVOKE';
const UPDATE_METADATA = 'UPDATE_METADATA';

function tokenMetaDataReducer(state, action) {
  let newState = { ...state };

  switch (action.type) {
    case UPDATE_METADATA:
      console.log('Metadata update trigerred.');
      newState = { ...action['payload'] };
      break;
    default:
      console.log('Invalid action type detected:');
      console.log(action.type);
      throw new Error();
  }

  return newState;
}

function tokenReducer(state, action) {
  const newState = { ...state };

  switch (action.type) {
    case ACTION_STORE:
      newState['apiToken'] = action.payload;
      break;
    case ACTION_REVOKE:
      newState['apiToken'] = null;
      break;
    default:
      console.log('Invalid action type detected:');
      console.log(action.type);
      throw new Error();
  }

  return newState;
}

function TokenMgmt() {
  const { authorizedFetch, user } = useContext(AuthorizationContext);

  const [tokenState, dispatchTokenState] = useReducer(tokenReducer, {
    apiToken: null,
  });

  const [tokenMetaDataState, dispatchTokenMetaData] = useReducer(
    tokenMetaDataReducer,
    {
      'token-stored?': false,
      'last-used': null,
    }
  );

  const updateTokenMetadata = useCallback(
    () => {
      authorizedFetch('/api/auth/token-metadata', { method: 'GET' })
        .then((response) => {
          if (response.ok) {
            return response.json();
          } else {
            console.log(
              'Error while retrieving token metadata. Returned response: ',
              response
            );
            throw new Error('Error while retrieving token metadata');
          }
        })
        .then((data) => {
          console.log('token-metadata result received:', data);

          dispatchTokenMetaData({ type: UPDATE_METADATA, payload: data });
        })
        .catch((error) => {
          console.log(
            'Error caught on authorizedFetch for token-metadata:',
            error
          );
        });
    },
    [authorizedFetch]
  );

  const defaultTokenInstructions =
    'No stored ID token to display.\n' +
    "Click the 'Store token' button below to store the current ID token and display it here.";

  function storeTokenHandler() {
    console.log('storeTokenHandler triggered.');

    authorizedFetch(`/api/auth/token`, {
      method: 'POST',
    }).then((response) => {
      if (response.ok) {
        dispatchTokenState({ type: ACTION_STORE, payload: user.id_token });
      } else {
        console.log('Error returned by /auth/token POST endpoint.');
        throw new Error('API endpoint for token storage returned error.');
      }
    });
  }

  function revokeTokenHandler() {
    console.log('revokeTokenHandler triggered.');

    authorizedFetch(`/api/auth/token`, {
      method: 'DELETE',
    }).then((response) => {
      if (response.ok) {
        dispatchTokenState({ type: ACTION_REVOKE });
      } else {
        console.log('Error returned by /auth/token DELETE endpoint.');
        throw new Error('API endpoint for token revoking returned error.');
      }
    });
  }

  useEffect(
    () => {
      updateTokenMetadata();
    },
    [tokenState, updateTokenMetadata]
  );

  return (
    <div>
      <span>
        Token stored?: {tokenMetaDataState['token-stored?'] ? 'Yes' : 'No'}
      </span>
      <br />
      <span>Token last used: {tokenMetaDataState['last-used'] || 'Never'}</span>
      <br />
      <textarea
        disabled={true}
        style={{ width: '100%', height: 65 }}
        value={tokenState.apiToken || defaultTokenInstructions}
      />
      <CopyToClipboard text={tokenState.apiToken}>
        <div>
          <button>Copy to clipboard</button>
        </div>
      </CopyToClipboard>
      <br />
      <Button variant="contained" onClick={storeTokenHandler}>
        Store token
      </Button>
      <Button variant="contained" onClick={revokeTokenHandler}>
        Revoke token
      </Button>
    </div>
  );
}

export default TokenMgmt;
