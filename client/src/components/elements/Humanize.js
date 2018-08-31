import PropTypes from 'prop-types';

const Humanize = ({children}) => {
  const [name] = (children || '').split('/').slice(-1);
  return name.replace(/(_|-)+/g, ' ');
};

Humanize.propTypes = {
  children: PropTypes.children,
};

export default Humanize;
