import PropTypes from 'prop-types';

const Humanize = ({ children, placeholder = 'Unknown' }) => {
  const [name] = (children || '').split('/').slice(-1);
  return name.replace(/(_|-)+/g, ' ') || placeholder;
};

Humanize.propTypes = {
  children: PropTypes.string,
  placeholder: PropTypes.oneOfType([PropTypes.element, PropTypes.string]),
};

export default Humanize;
