import * as React from 'react';

import { ReactNativeBrotherPrintersViewProps } from './ReactNativeBrotherPrinters.types';

export default function ReactNativeBrotherPrintersView(props: ReactNativeBrotherPrintersViewProps) {
  return (
    <div>
      <span>{props.name}</span>
    </div>
  );
}
