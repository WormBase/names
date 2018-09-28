import React, { Component } from 'react';  // eslint-disable-line no-unused-vars
import PropTypes from 'prop-types';
import moment from 'moment';
import { Tooltip } from '@material-ui/core';

class Timestamp extends Component {
  render() {
    const {time} = this.props;
    return (
      <Tooltip title={time} placement="top-start">
        <span>
          {moment(time).fromNow()}
        </span>
      </Tooltip>
    );
  }
}

Timestamp.propTypes = {
  time: PropTypes.any.isRequired,
};

export default Timestamp;
