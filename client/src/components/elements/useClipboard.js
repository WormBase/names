import { useState, useEffect } from 'react';
import copy from 'copy-to-clipboard';

export default function useClipboard(content) {
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    copy(content);
    setCopied(true);
  }

  useEffect(
    () => {
      if (copied) {
        const timer = setTimeout(() => {
          setCopied(false);
        }, 2000);
        return () => {
          clearTimeout(timer);
          setCopied(false);
        };
      }
      return () => {};
    },
    [copied]
  );

  return { copied, handleCopy };
}
