import PropTypes from 'prop-types';

const Humanize = ({
  children,
  placeholder = 'Unknown',
  postProcessor,
  capitalize = false,
}) => {
  if (!children) {
    return placeholder;
  }
  let [humanizedText] = (children || '').split('/').slice(-1);
  humanizedText = humanizedText.replace(/(_|-)+/g, ' ');

  if (capitalize) {
    humanizedText =
      humanizedText.charAt(0).toUpperCase() + humanizedText.slice(1);
  }

  if (postProcessor) {
    humanizedText = postProcessor(humanizedText);
  }

  return humanizedText;
};

Humanize.propTypes = {
  children: PropTypes.string,
  capitalize: PropTypes.bool,
  placeholder: PropTypes.oneOfType([PropTypes.element, PropTypes.string]),
  postProcessor: PropTypes.func,
};

export default Humanize;
