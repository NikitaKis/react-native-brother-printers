import { NativeEventEmitter, NativeModules, Platform } from "react-native";
import BrotherPrinters from "./ReactNativeBrotherPrintersModule";

const { ReactNativeBrotherPrinters: BrotherPrintersIos } = NativeModules;

export const LabelSizeDieCutW17H87 = 1;
export const LabelSizeDieCutW23H23 = 2;
export const LabelSizeDieCutW29H42 = 3;
export const LabelSizeDieCutW29H90 = 4;
export const LabelSizeDieCutW38H90 = 5;
export const LabelSizeDieCutW39H48 = 6;
export const LabelSizeDieCutW52H29 = 7;
export const LabelSizeDieCutW17H54 = 0;
export const LabelSizeDieCutW62H29 = 8;
export const LabelSizeDieCutW62H100 = 9;
export const LabelSizeDieCutW60H86 = 10;
export const LabelSizeDieCutW54H29 = 11;
export const LabelSizeDieCutW102H51 = 12;
export const LabelSizeDieCutW102H152 = 13;
export const LabelSizeDieCutW103H164 = 13;
export const LabelSizeRollW12 = 14;
export const LabelSizeRollW29 = 15;
export const LabelSizeRollW38 = 16;
export const LabelSizeRollW50 = 17;
export const LabelSizeRollW54 = 18;
export const LabelSizeRollW62 = 19;
export const LabelSizeRollW62RB = 20;
export const LabelSizeRollW102 = 21;
export const LabelSizeRollW103 = 22;
export const LabelSizeDTRollW90 = 23;
export const LabelSizeDTRollW102 = 24;
export const LabelSizeDTRollW102H51 = 25;
export const LabelSizeDTRollW102H152 = 26;

export const LabelSize = {
  LabelSizeDieCutW17H54,
  LabelSizeDieCutW17H87,
  LabelSizeDieCutW23H23,
  LabelSizeDieCutW29H42,
  LabelSizeDieCutW29H90,
  LabelSizeDieCutW38H90,
  LabelSizeDieCutW39H48,
  LabelSizeDieCutW52H29,
  LabelSizeDieCutW62H29,
  LabelSizeDieCutW62H100,
  LabelSizeDieCutW60H86,
  LabelSizeDieCutW54H29,
  LabelSizeDieCutW102H51,
  LabelSizeDieCutW102H152,
  LabelSizeDieCutW103H164,
  LabelSizeRollW12,
  LabelSizeRollW29,
  LabelSizeRollW38,
  LabelSizeRollW50,
  LabelSizeRollW54,
  LabelSizeRollW62,
  LabelSizeRollW62RB,
  LabelSizeRollW102,
  LabelSizeRollW103,
  LabelSizeDTRollW90,
  LabelSizeDTRollW102,
  LabelSizeDTRollW102H51,
  LabelSizeDTRollW102H152,
}

if (!BrotherPrinters && !BrotherPrintersIos) {
  console.warn(
    "No native BrotherPrinters module found, are you sure the react-native-brother-printers's module is linked properly?"
  );
}

type Device = {
  "ipAddress": string,
  "modelName": string,
}

type PrintParams = {
  autoCut: boolean,
  labelSize: number,
  labelWidth?: number, // Optional, used for roll labels
  labelHeight?: number, // Optional, used for roll labels
}
const {
  discoverPrinters: _discoverPrinters,
  pingPrinter: _pingPrinter,
  printImage: _printImage,
  printPDF: _printPDF,
} = Platform.OS === 'ios' ? BrotherPrintersIos : BrotherPrinters;

/**
 * Starts the discovery process for brother printers
 *
 * @param params
 * @param params.V6             If we should searching using IP v6.
 * @param params.printerName    If we should name the printer something specific.
 *
 * @return {Promise<void>}
 */
export async function discoverPrinters(params = {}) {
  if (Platform.OS === 'ios') {
    return BrotherPrintersIos.discoverPrinters(params);
  } else {
    return BrotherPrinters.discover();
  }

}

/**
 * Prints an image
 *
 * @param device                  Device object
 * @param uri                     URI of image wanting to be printed
 * @param params
 * @param params.autoCut            Boolean if the printer should auto cut the receipt/label
 * @param params.labelSize          Label size that we are printing with
 *
 * @return {Promise<*>}
 */
export async function printImage(device: Device, uri: string, params: PrintParams) {
  if (Platform.OS === 'ios') {
    if (!params.labelSize) {
      return new Error("Label size must be given when printing a label");
    }

    return _printImage(device, uri, params);
  }
  return BrotherPrinters.printImage(uri, device.ipAddress);
}

const listeners = BrotherPrintersIos && new NativeEventEmitter(BrotherPrintersIos);

export function registerBrotherListener(key: any, method: any) {
  return listeners && listeners.addListener(key, method);
}
// export {
//   ReactNativeBrotherPrintersView,
//   ReactNativeBrotherPrintersViewProps,
//   ChangeEventPayload,
// };
