import React, { Component } from 'react'; // eslint-disable-line no-unused-vars
import PropTypes from 'prop-types';
import moment from 'moment';
import { Tooltip } from '@material-ui/core';

class Timestamp extends Component {
  render() {
    const { time, placeholder = 'Unknown' } = this.props;
    return time ? (
      <Tooltip title={time} placement="top-start">
        <span>{moment(time).fromNow()}</span>
      </Tooltip>
    ) : (
      placeholder
    );
  }
}

Timestamp.propTypes = {
  time: PropTypes.any.isRequired,
  placeholder: PropTypes.oneOfType([PropTypes.element, PropTypes.string]),
};

export default Timestamp;
