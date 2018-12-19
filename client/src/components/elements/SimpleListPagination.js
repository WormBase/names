import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { withStyles, Button } from '@material-ui/core';
import ArrowBackIcon from '@material-ui/icons/KeyboardArrowLeft';
import ArrowForwardIcon from '@material-ui/icons/KeyboardArrowRight';

class SimpleListPagination extends Component {
  constructor(props) {
    super(props);
    this.state = {
      page: 0,
    };
  }

  handlePageIncrement = () => {
    this.setState(
      (prevState) => ({
        page: Math.min(prevState.page + 1, this.maxPage()),
      }),
      this.pageChangeCallback
    );
  };

  handlePageDecrement = () => {
    this.setState(
      (prevState) => ({
        page: Math.max(0, prevState.page - 1),
      }),
      this.pageChangeCallback
    );
  };

  pageChangeCallback = () => {
    const { page } = this.state;
    const { pageSize } = this.props;
    if (this.props.onPageChange) {
      this.props.onPageChange(page * pageSize, (page + 1) * pageSize);
    }
  };

  componentDidMount() {
    this.pageChangeCallback();
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevProps.items !== this.props.items) {
      this.setState({
        page: 0,
      });
    }
  }

  maxPage = () => Math.ceil(this.props.items.length / this.props.pageSize) - 1;

  render() {
    const { page } = this.state;
    const { items, pageSize, classes } = this.props;
    const pageItems = this.props.items.slice(
      page * pageSize,
      (page + 1) * pageSize
    );
    return (
      <div className={classes.root}>
        {this.props.children({
          pageItems: pageItems,
          navigation:
            items.length > pageSize ? (
              <div className={classes.navigation}>
                <Button onClick={this.handlePageDecrement} disabled={page <= 0}>
                  <ArrowBackIcon />
                </Button>
                <Button
                  onClick={this.handlePageIncrement}
                  disabled={page >= this.maxPage()}
                >
                  <ArrowForwardIcon />
                </Button>
              </div>
            ) : null,
        })}
      </div>
    );
  }
}

SimpleListPagination.propTypes = {
  classes: PropTypes.object.isRequired,
  items: PropTypes.arrayOf(PropTypes.object),
  pageSize: PropTypes.number,
  onPageChange: PropTypes.func,
};

SimpleListPagination.defaultProps = {
  items: [],
  pageSize: 5,
};

const styles = (theme) => ({
  navigation: {
    display: 'flex',
    justifyContent: 'space-between',
    '& > *': {
      //flex: '1 1 auto',
    },
  },
});

export default withStyles(styles)(SimpleListPagination);
