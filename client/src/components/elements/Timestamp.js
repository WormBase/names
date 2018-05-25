import React, { Component } from 'react';
import PropTypes from 'prop-types';
import moment from 'moment-timezone';

class Timestamp extends Component {
  constructor(props) {
    super(props);
    this.timezone = moment.tz.guess();
  }

  render() {
    const {time, formatter} = this.props;
    return (
      moment.tz(time, this.timezone).format(formatter || 'MMM D YYYY HH:mm z')
    );
  }
}

Timestamp.propTypes = {
  time: PropTypes.any.isRequired,
  formatter: PropTypes.string,
};

export default Timestamp;
