import { useEffect } from 'react';

export function useTitle(title) {
  useEffect(() => {
    if (title) {
      const prevTitle = document.title;
      document.title = title;
      return () => {
        document.title = prevTitle;
      };
    } else {
      return undefined;
    }
  });
}
