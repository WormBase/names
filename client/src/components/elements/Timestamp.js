import React, { Component } from 'react';  // eslint-disable-line no-unused-vars
import PropTypes from 'prop-types';
import moment from 'moment';

class Timestamp extends Component {
  render() {
    const {time} = this.props;
    return moment(time).fromNow();
  }
}

Timestamp.propTypes = {
  time: PropTypes.any.isRequired,
};

export default Timestamp;
