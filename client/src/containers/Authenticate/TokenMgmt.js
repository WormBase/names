import React, { useContext, useReducer } from 'react';
import { Button } from '../../components/elements';

import AuthorizationContext from '../../containers/Authenticate/AuthorizationContext';

const TokenMgmt = (props) => {
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
    const new_state = { ...state };

    switch (action.type) {
      case ACTION_STORE:
        console.log('Storing apiToken in new state.');
        new_state['apiToken'] = user.id_token;
        break;
      case ACTION_REVOKE:
        console.log('Revoking apiToken from new state.');
        new_state['apiToken'] = null;
        break;
      default:
        console.log('Invalid action type detected:');
        console.log(action.type);
        throw new Error();
    }

    return new_state;
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
        disabled="true"
        value={tokenState.apiToken || defaultTokenInstructions}
      />
      <br />
      <Button variant="raised" onClick={storeTokenHandler}>
        Store token
      </Button>
      <Button variant="raised" onClick={revokeTokenHandler}>
        Revoke token
      </Button>
    </div>
  );
};

export default TokenMgmt;
