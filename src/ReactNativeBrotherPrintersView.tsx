import { requireNativeViewManager } from 'expo-modules-core';
import * as React from 'react';

import { ReactNativeBrotherPrintersViewProps } from './ReactNativeBrotherPrinters.types';

const NativeView: React.ComponentType<ReactNativeBrotherPrintersViewProps> =
  requireNativeViewManager('ReactNativeBrotherPrinters');

export default function ReactNativeBrotherPrintersView(props: ReactNativeBrotherPrintersViewProps) {
  return <NativeView {...props} />;
}
