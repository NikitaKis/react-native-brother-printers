import { NativeModulesProxy, EventEmitter, Subscription } from 'expo-modules-core';

// Import the native module. On web, it will be resolved to ReactNativeBrotherPrinters.web.ts
// and on native platforms to ReactNativeBrotherPrinters.ts
import ReactNativeBrotherPrintersModule from './ReactNativeBrotherPrintersModule';
import ReactNativeBrotherPrintersView from './ReactNativeBrotherPrintersView';
import { ChangeEventPayload, ReactNativeBrotherPrintersViewProps } from './ReactNativeBrotherPrinters.types';

// Get the native constant value.
export const PI = ReactNativeBrotherPrintersModule.PI;

export function hello(): string {
  return ReactNativeBrotherPrintersModule.hello();
}

export async function setValueAsync(value: string) {
  return await ReactNativeBrotherPrintersModule.setValueAsync(value);
}

const emitter = new EventEmitter(ReactNativeBrotherPrintersModule ?? NativeModulesProxy.ReactNativeBrotherPrinters);

export function addChangeListener(listener: (event: ChangeEventPayload) => void): Subscription {
  return emitter.addListener<ChangeEventPayload>('onChange', listener);
}

export { ReactNativeBrotherPrintersView, ReactNativeBrotherPrintersViewProps, ChangeEventPayload };
