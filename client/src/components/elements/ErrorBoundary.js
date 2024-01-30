import React from 'react';
import PropTypes from 'prop-types';
import Typography from '@material-ui/core/Typography';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import { withStyles } from '@material-ui/core/styles';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  componentDidCatch() {
    // Display fallback UI
    // eslint-disable-next-line
    this.setState({ hasError: true });
    // You can also log the error to an error reporting service
    // logErrorToMyService(error, info);
  }

  render() {
    const { classes } = this.props;
    if (this.state.hasError) {
      // You can render any custom fallback UI
      return (
        <Card elevation={0} className={classes.card}>
          <CardContent>
            <Typography variant="h4">A problem occurred</Typography>
            <Typography variant="subtitle1">
              Please <a href={`mailto:developers.wormbase.org`}>let us know</a>.
            </Typography>
          </CardContent>
        </Card>
      );
    }
    return this.props.children;
  }
}

ErrorBoundary.propTypes = {
  classes: PropTypes.object.isRequired,
  children: PropTypes.any,
};

const styles = (theme) => ({
  card: {
    backgroundColor: theme.palette.background.default,
  },
});

export default withStyles(styles)(ErrorBoundary);
