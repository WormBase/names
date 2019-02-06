import PropTypes from 'prop-types';

const Humanize = ({ children, placeholder = 'Unknown', postProcessor }) => {
  if (!children) {
    return placeholder;
  }
  const [name] = (children || '').split('/').slice(-1);
  const humanizedText = name.replace(/(_|-)+/g, ' ');
  return postProcessor ? postProcessor(humanizedText) : humanizedText;
};

Humanize.propTypes = {
  children: PropTypes.string,
  placeholder: PropTypes.oneOfType([PropTypes.element, PropTypes.string]),
  postProcessor: PropTypes.func,
};

export default Humanize;
