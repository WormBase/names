import React, { useCallback, useReducer, useEffect } from 'react';
import PropTypes from 'prop-types';
import { withRouter, Link } from 'react-router-dom';
import { Prompt } from 'react-router';
import CircularProgress from '@material-ui/core/CircularProgress';
import {
  BaseForm,
  PROGRESS_BUTTON_PENDING,
  PROGRESS_BUTTON_READY,
  useClipboard,
} from '../../components/elements';

import EntityNotFound from './EntityNotFound';
import { useDataFetch } from '../../containers/Authenticate';
import { mockFetchOrNot } from '../../mock';

function EntityEdit({
  entityType,
  wbId: id,
  apiPrefix,
  renderDisplayName,
  history,
  children,
}) {
  const [state, dispatch] = useReducer(reducer, {
    shortMessage: null,
    shortMessageVariant: 'info',
    dialog: null,
  });

  function reducer(state, action = {}) {
    const { payload = {} } = action;
    console.log(action);
    switch (action.type) {
      case 'DIALOG_OPEN':
        return {
          ...state,
          dialog: payload.operation,
        };
      case 'DIALOG_CLOSE':
        return {
          ...state,
          dialog: null,
        };
      case 'DIALOG_OPERATION_SUCCESS':
        return {
          ...state,
          shortMessage: payload.shortMessage,
          shortMessageVariant: payload.shortMessageVariant,
          dialog: null,
        };
      case 'MESSAGE_SHOW':
        return {
          ...state,
          shortMessage: payload.shortMessage,
          shortMessageVariant: payload.shortMessageVariant,
        };
      case 'MESSAGE_DISMISS':
        return {
          ...state,
          shortMessage: null,
          shortMessageVariant: 'info',
        };
      default:
        throw new Error();
    }
  }

  const memoizedFetchFunc = useCallback(
    (authorizedFetch) => {
      return mockFetchOrNot(
        (mockFetch) => {
          const historyMock = [
            {
              how: 'agent/web',
              what: 'event/merge-genes',
              who: {
                'person/id': 'WBPerson12346',
              },
              when: '2018-08-09T22:09:16Z',
              'merged-from': {
                id: 'WBGene00303223',
              },
              'merged-into': {
                id: id,
              },
            },
            {
              how: 'agent/web',
              what: 'event/update-gene',
              who: {
                'person/id': 'WBPerson12346',
              },
              when: '2018-08-08T17:27:31Z',
            },
            {
              how: 'agent/web',
              what: 'event/update-gene',
              who: {
                'person/id': 'WBPerson12346',
              },
              when: '2018-08-08T17:27:22Z',
            },
            {
              how: 'agent/web',
              what: 'event/split-gene',
              who: {
                'person/id': 'WBPerson12346',
              },
              when: '2018-08-08T16:50:46Z',
              'split-from': {
                id: id,
              },
              'split-into': {
                id: 'WBGene00303222',
              },
            },
            {
              how: 'agent/web',
              what: 'event/split-gene',
              who: {
                'person/id': 'WBPerson12346',
              },
              when: '2018-08-08T15:21:07Z',
              'split-from': {
                id: id,
              },
              'split-into': {
                id: 'WBGene00303219',
              },
            },
            {
              how: 'agent/web',
              what: 'event/new-gene',
              who: {
                id: 'WBPerson12346',
              },
              when: '2018-07-23T15:25:17Z',
            },
          ];

          return mockFetch.get('*', {
            species: 'Caenorhabditis elegans',
            'cgc-name': 'abi-1',
            status: 'live',
            biotype: 'cds',
            id: id,
            history: historyMock,
          });
        },
        () => {
          return authorizedFetch(`${apiPrefix}/${id}`);
        }
      );
    },
    [apiPrefix, id]
  );

  const {
    data: responseContent,
    dataTimestamp,
    isLoading,
    isError,
    isSuccess,
    refetch,
  } = useDataFetch(memoizedFetchFunc, {});

  const {
    isSuccess: isSubmitSuccess,
    isLoading: isSubmitInProgress,
    isError: submitError,
    setFetchFunc: setSubmitFetchFunc,
  } = useDataFetch(null, {});

  const { history: changes, ...data } = responseContent;
  const wbId = data.id;
  const disabled =
    isLoading || isSubmitInProgress || (data && data['status'] === 'dead');

  const getPermanentUrl = useCallback(
    () => {
      return wbId ? `/${entityType}/id/${wbId}` : null;
    },
    [entityType, wbId]
  );

  useEffect(
    () => {
      if (isSuccess) {
        const permanentUrl = getPermanentUrl();
        if (permanentUrl && history.location.pathname !== permanentUrl) {
          history.replace(permanentUrl);
        }
      }
    },
    [isSuccess, getPermanentUrl, history]
  );

  useEffect(
    () => {
      if (isSubmitSuccess) {
        refetch();
        dispatch({
          type: 'MESSAGE_SHOW',
          payload: {
            shortMessageVariant: 'success',
            shortMessage: 'Update successful',
          },
        });
      }
    },
    [isSubmitSuccess, refetch]
  );

  const { copied, handleCopy } = useClipboard(
    `${renderDisplayName(data)} ${wbId}`
  );

  return isError ? (
    <EntityNotFound entityType={entityType} wbId={id} />
  ) : isLoading && dataTimestamp === 0 ? (
    /* initial render only, don't clear page if refetching after submit */
    <CircularProgress />
  ) : (
    <BaseForm key={dataTimestamp} data={data} disabled={disabled}>
      {({
        withFieldData,
        getFormData,
        getFormDataModified,
        getFormProps,
        dirtinessContext,
        resetData,
      }) => {
        return (
          <React.Fragment>
            {children({
              dataCommitted: data,
              changes: changes,
              profileContext: {
                entityType: entityType,
                wbId: wbId,
                dataCommitted: data,
                changes: changes,
                errorMessage: submitError,
                message: state.shortMessage,
                messageVariant: state.shortMessageVariant,
                onMessageClose: () => dispatch({ type: 'MESSAGE_DISMISS' }),
                buttonCopyProps: {
                  onClick: handleCopy,
                  children: copied ? 'Copied' : 'Copy',
                },
                buttonResetProps: {
                  onClick: resetData,
                  disabled: disabled,
                },
                buttonSubmitProps: {
                  status:
                    isSubmitInProgress || isLoading
                      ? PROGRESS_BUTTON_PENDING
                      : PROGRESS_BUTTON_READY,
                  onClick: () => {
                    const { data = {}, prov: provenance } =
                      getFormDataModified() || {};
                    if (Object.keys(data).length === 0) {
                      dispatch({
                        type: 'MESSAGE_SHOW',
                        payload: {
                          shortMessage:
                            "You didn't modify anything in the form. No change is submitted",
                          shortMessageVariant: 'warning',
                        },
                      });
                      return;
                    }
                    setSubmitFetchFunc((authorizedFetch) => {
                      return mockFetchOrNot(
                        (mockFetch) => {
                          return mockFetch.put('*', {
                            updated: {
                              ...data,
                            },
                          });
                        },
                        () => {
                          const dataSubmit = Object.keys(data).reduce(
                            (result, key) => {
                              if (
                                key !== 'split-from' &&
                                key !== 'split-into' &&
                                key !== 'merged-from' &&
                                key !== 'merged-into'
                              ) {
                                result[key] = data[key];
                              }
                              return result;
                            },
                            {}
                          );
                          return authorizedFetch(`${apiPrefix}/${wbId}`, {
                            method: 'PUT',
                            body: JSON.stringify({
                              data: dataSubmit,
                              prov: provenance,
                            }),
                          });
                        }
                      );
                    }, {});
                  },
                  disabled: isSubmitInProgress || disabled,
                },
                withFieldData,
                dirtinessContext,
              },
              formContext: {
                entityType: entityType,
                withFieldData,
              },
              getOperationProps: (operation) => ({
                onClick: () => {
                  dispatch({ type: 'DIALOG_OPEN', payload: { operation } });
                },
              }),
              getDialogProps: (operation) => ({
                entityType: entityType,
                apiPrefix: apiPrefix,
                wbId: wbId,
                name: renderDisplayName(data),
                data: data,
                open: state.dialog === operation,
                onClose: () => dispatch({ type: 'DIALOG_CLOSE' }),
                onSubmitSuccess: ({ created = {}, updated = {} }) => {
                  dispatch({
                    type: 'DIALOG_OPERATION_SUCCESS',
                    payload: {
                      shortMessage: (
                        <span>
                          {operation} successful!{' '}
                          {operation === 'split' ? (
                            <React.Fragment>
                              Split into{' '}
                              <Link to={`/${entityType}/id/${created.id}`}>
                                {created.id}
                              </Link>
                            </React.Fragment>
                          ) : null}
                          {operation === 'merge' ? (
                            <React.Fragment>
                              Merged into{' '}
                              <Link to={`/${entityType}/id/${updated.id}`}>
                                {updated.id}
                              </Link>
                            </React.Fragment>
                          ) : null}
                        </span>
                      ),
                      shortMessageVariant: 'success',
                    },
                  });
                  refetch();
                },
              }),
              // from BaseForm
              withFieldData,
              getFormData,
              getFormDataModified,
              dirtinessContext,
            })}
            {dirtinessContext(({ dirty }) => (
              <Prompt
                when={dirty}
                message="Form contains unsubmitted content, which will be lost when you leave. Are you sure you want to leave?"
              />
            ))}
          </React.Fragment>
        );
      }}
    </BaseForm>
  );
}

EntityEdit.propTypes = {
  wbId: PropTypes.string.isRequired,
  entityType: PropTypes.string.isRequired,
  apiPrefix: PropTypes.string.isRequired,
  renderDisplayName: PropTypes.func,
  history: PropTypes.shape({
    replace: PropTypes.func.isRequired,
  }).isRequired,
};

export default withRouter(EntityEdit);
