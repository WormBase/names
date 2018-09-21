import React from 'react';
import PropTypes from 'prop-types';

class SimpleAjax extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      errorMessage: null,
      status: null,
    };
  }

  handleSubmit = (data) => {
    const {onSubmitStart, onSubmitSuccess, onSubmitError} = this.props;
    this.setState({
      status: 'SUBMITTED',
    }, () => {
      onSubmitStart && onSubmitStart();
      console.log(data);
      this.props.submitter(data).then((response) => {
        return Promise.all([response, response.json()]);
      }).then(([response, body]) => {
        if (response.ok) {
          this.setState({
            errorMessage: null,
            status: 'COMPLETE',
          })
          onSubmitSuccess && onSubmitSuccess({...body.updated});
        } else {
          this.setState({
            errorMessage: body,
            status: 'COMPLETE',
          });
          onSubmitError && onSubmitError(body);
        }
      }).catch((e) => console.log('error', e));
    });
  }

  render() {
    const {data} = this.props;
    return this.props.children({
      errorMessage: this.state.errorMessage,
      status: this.state.status,
      handleSubmit: () => this.handleSubmit(typeof data === 'function' ? data() : data),
    });
  }
}

SimpleAjax.propTypes = {
  submitter: PropTypes.func,
  data: PropTypes.any,
  onSubmitStart: PropTypes.func,
  onSubmitSuccess: PropTypes.func,
  onSubmitError: PropTypes.func,
};

export default SimpleAjax;
