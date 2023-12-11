import React, { useContext, useReducer } from 'react';
import { Button } from '../../components/elements';
import { CopyToClipboard } from 'react-copy-to-clipboard';

import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';

function TokenMgmt() {
  const ACTION_STORE = 'STORE';
  const ACTION_REVOKE = 'REVOKE';

  const { authorizedFetch, user } = useContext(AuthorizationContext);

  const [tokenState, dispatchTokenState] = useReducer(tokenReducer, {
    apiToken: null,
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
    });

    dispatchTokenState({ type: ACTION_STORE });
  }

  function revokeTokenHandler() {
    console.log('revokeTokenHandler triggered.');

    authorizedFetch(`/api/auth/token`, {
      method: 'DELETE',
    });

    dispatchTokenState({ type: ACTION_REVOKE });
  }

  return (
    <div>
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
