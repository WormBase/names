import React, { useContext, useReducer, useEffect } from 'react';
import { CopyToClipboard } from 'react-copy-to-clipboard';

import { Button } from '../../components/elements';
import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';

function TokenMgmt() {
  const ACTION_STORE = 'STORE';
  const ACTION_REVOKE = 'REVOKE';
  const UPDATE_METADATA = 'UPDATE_METADATA';

  const { authorizedFetch, user } = useContext(AuthorizationContext);

  const [tokenState, dispatchTokenState] = useReducer(tokenReducer, {
    apiToken: null,
    tokenMetadata: {
      'token-stored?': false,
      'last-used': null,
    },
  });

  const defaultTokenInstructions =
    'No stored ID token to display.\n' +
    "Click the 'Store token' button below to store the current ID token and display it here.";

  function tokenReducer(state, action) {
    const newState = { ...state };

    switch (action.type) {
      case ACTION_STORE:
        newState['apiToken'] = user.id_token;
        break;
      case ACTION_REVOKE:
        newState['apiToken'] = null;
        break;
      case UPDATE_METADATA:
        console.log('Metadata update trigerred.');
        newState['tokenMetadata'] = { ...action.payload };
        break;
      default:
        console.log('Invalid action type detected:');
        console.log(action.type);
        throw new Error();
    }

    return newState;
  }

  function storeTokenHandler() {
    console.log('storeTokenHandler triggered.');

    authorizedFetch(`/api/auth/token`, {
      method: 'POST',
    }).then((response) => {
      if (response.ok) {
        updateTokenMetadata();
      } else {
        console.log('Error returned by /auth/token POST endpoint.');
      }
    });

    dispatchTokenState({ type: ACTION_STORE });
  }

  function revokeTokenHandler() {
    console.log('revokeTokenHandler triggered.');

    authorizedFetch(`/api/auth/token`, {
      method: 'DELETE',
    }).then((response) => {
      if (response.ok) {
        updateTokenMetadata();
      } else {
        console.log('Error returned by /auth/token DELETE endpoint.');
      }
    });

    dispatchTokenState({ type: ACTION_REVOKE });
  }

  function updateTokenMetadata() {
    authorizedFetch('/api/auth/token-metadata', { method: 'GET' })
      .then((response) => {
        return Promise.all([response, response.json()]);
      })
      .then(([response, data]) => {
        if (response.ok) {
          console.log('authorizedFetch data results:', data);
          dispatchTokenState({ type: UPDATE_METADATA, payload: data });
        } else {
          console.log('Error while retrieving token metadata.');
        }
      });
  }

  useEffect(() => {
    updateTokenMetadata();
  }, []);

  return (
    <div>
      <span>
        Token stored?:{' '}
        {tokenState.tokenMetadata['token-stored?'] ? 'Yes' : 'No'}
      </span>
      <br />
      <span>
        Token last used: {tokenState.tokenMetadata['last-used'] || 'Never'}
      </span>
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
