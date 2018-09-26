export function createOpenOnlyTypeChecker(typeChecker) {
  return function(props, propName, ...rest) {
    if (props.open) {
      return typeChecker(props, propName, ...rest);
    }
  }
}
