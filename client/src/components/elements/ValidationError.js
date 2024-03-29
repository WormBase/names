import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Card,
  CardActions,
  CardContent,
  Collapse,
  Typography,
  withStyles,
} from '@material-ui/core';
import KeyboardArrowUpIcon from '@material-ui/icons/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@material-ui/icons/KeyboardArrowDown';

class ValidationError extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      expanded: false,
    };
  }

  handleCollapseToggle = () => {
    this.setState((prevState) => ({
      expanded: !prevState.expanded,
    }));
  };

  renderActions = () => {
    return this.props.problems ? (
      <CardActions className={this.props.classes.actions}>
        <Button onClick={this.handleCollapseToggle}>
          {this.state.expanded ? (
            <KeyboardArrowUpIcon className={this.props.classes.leftIcon} />
          ) : (
            <KeyboardArrowDownIcon className={this.props.classes.leftIcon} />
          )}
          {this.state.expanded ? 'Show less' : 'Show more'}
        </Button>
      </CardActions>
    ) : null;
  };

  render() {
    const { classes, message, problems, errors = [] } = this.props;
    const problemText = problems;
    return message || problems ? (
      <Card card className={classes.root}>
        <CardContent>
          <Typography color="error">{message || 'Error'}</Typography>
          {errors.map(({ name, value, reason, regexp }) => (
            <ul>
              <li>
                <Typography key={name} color="error">
                  {name}:{' '}
                  <em>
                    <strong>{value}</strong>
                  </em>{' '}
                  {reason}
                  {regexp ? <pre> {regexp}</pre> : null}
                </Typography>
              </li>
            </ul>
          ))}
        </CardContent>
        {this.renderActions()}
        <Collapse in={this.state.expanded}>
          <CardContent className={classes.problems}>
            <pre>{problemText}</pre>
          </CardContent>
        </Collapse>
      </Card>
    ) : null;
  }
}

ValidationError.propTypes = {
  classes: PropTypes.object.isRequired,
  message: PropTypes.string,
  errors: PropTypes.arrayOf(
    PropTypes.shape({
      name: PropTypes.string,
      value: PropTypes.string,
      reason: PropTypes.string,
      regexp: PropTypes.string,
    })
  ),
  problems: PropTypes.object,
};

const styles = (theme) => ({
  root: {
    margin: `${theme.spacing(4)}px 0`,
    backgroundColor: theme.palette.background.default,
  },
  message: {
    color: theme.palette.error.main,
  },
  problems: {
    color: theme.palette.error.main,
    marginTop: theme.spacing(-2),
    backgroundColor: theme.palette.background.paper,
  },
  actions: {},
  leftIcon: {
    marginRight: theme.spacing(1),
  },
});

export default withStyles(styles)(ValidationError);
