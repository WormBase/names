import React, { useReducer } from 'react';
// import PropTypes from 'prop-types';
import { Button } from '../../components/elements';

const TokenMgmt = (props) => {
  const ACTION_STORE = 'STORE';
  const ACTION_REVOKE = 'REVOKE';

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
        new_state['apiToken'] =
          'API TOKEN str. To be retrieved from the identity state (profile?). ' +
          Date.now();
        break;
      case ACTION_REVOKE:
        console.log('Revoking apiToken from new state.');
        new_state['apiToken'] = '';
        break;
      default:
        console.log('Invalid action type detected:');
        console.log(action.type);
        throw new Error();
    }

    return new_state;
  }

  return (
    <div>
      <textarea
        disabled="true"
        value={tokenState.apiToken || defaultTokenInstructions}
      />
      <br />
      <Button
        variant="raised"
        onClick={() => {
          //TODO: action the token storage
          dispatchTokenState({ type: ACTION_STORE });
        }}
      >
        Store token
      </Button>
      <Button
        variant="raised"
        onClick={() => {
          //TODO: action the token storage
          dispatchTokenState({ type: ACTION_REVOKE });
        }}
      >
        Revoke token
      </Button>
    </div>
  );
};

// TokenMgmt.propTypes = {
// };

export default TokenMgmt;
