// import {
//   NativeModulesProxy,
//   EventEmitter,
//   Subscription,
// } from "expo-modules-core";

// Import the native module. On web, it will be resolved to ReactNativeBrotherPrinters.web.ts
// and on native platforms to ReactNativeBrotherPrinters.ts

import ReactNativeBrotherPrintersModule from "./ReactNativeBrotherPrintersModule";

// // Get the native constant value.
// export const PI = ReactNativeBrotherPrintersModule.PI;

// export function hello(): string {
//   return ReactNativeBrotherPrintersModule.hello();
// }

// export async function setValueAsync(value: string) {
//   return await ReactNativeBrotherPrintersModule.setValueAsync(value);
// }

// const emitter = new EventEmitter(
//   ReactNativeBrotherPrintersModule ??
//     NativeModulesProxy.ReactNativeBrotherPrinters
// );

// export function addChangeListener(
//   listener: (event: ChangeEventPayload) => void
// ): Subscription {
//   return emitter.addListener<ChangeEventPayload>("onChange", listener);
// }

export const discover = () => {
  const discovered = ReactNativeBrotherPrintersModule?.discover?.();
  console.log("discovered", discovered);
  // TODO parse discovered to esential data
  // https://support.brother.com/g/s/es/htmldoc/mobilesdk/reference/android_v4/channel.html#newusbchannel
  return discovered;
};
export const printImage = (image: string) => {
  return ReactNativeBrotherPrintersModule?.printImage?.(image);
};
export const getPaperSize = () => {
  return ReactNativeBrotherPrintersModule?.getPaperSize?.();
};
export const openCashDrawer = () => {
  return ReactNativeBrotherPrintersModule?.openCashDrawer?.();
};

// export {
//   ReactNativeBrotherPrintersView,
//   ReactNativeBrotherPrintersViewProps,
//   ChangeEventPayload,
// };
