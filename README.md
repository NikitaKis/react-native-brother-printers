# react-native-brother-printers

My new module

# API documentation

- [Documentation for the main branch](https://github.com/expo/expo/blob/main/docs/pages/versions/unversioned/sdk/react-native-brother-printers.md)
- [Documentation for the latest stable release](https://docs.expo.dev/versions/latest/sdk/react-native-brother-printers/)

# Installation in managed Expo projects

For [managed](https://docs.expo.dev/archive/managed-vs-bare/) Expo projects, please follow the installation instructions in the [API documentation for the latest stable release](#api-documentation). If you follow the link and there is no documentation available then this library is not yet usable within managed projects &mdash; it is likely to be included in an upcoming Expo SDK release.

# Installation in bare React Native projects

For bare React Native projects, you must ensure that you have [installed and configured the `expo` package](https://docs.expo.dev/bare/installing-expo-modules/) before continuing.

### Add the package to your npm dependencies

```
npm install react-native-brother-printers
```

### Configure for iOS

Run `npx pod-install` after installing the npm package.


### Configure for Android

This package includes Android native printing via Brother's Android SDK (`android/libs/BrotherPrintLibrary.aar`).

Supported Android flows in this repository:

- `discoverPrinters()` for network discovery
- `discoverBluetoothPrinters()` for classic Bluetooth discovery
- `discoverPrintersUsb()` for USB discovery
- `pingPrinter(ipAddress)` to validate reachability
- `printImage(device, uri, options)` for printing image labels

Expected `printImage` params:

- `device.modelName` (recommended)
- `device.ipAddress` for network printers, or `device.serialNumber` for Bluetooth printers
- `options.labelSize` using exported `LabelSize` constants
- `options.autoCut` (optional, defaults to `true`)

`uri` can be:

- Absolute file path
- `file://` URI
- `content://` URI
- `http://` or `https://` URL (downloaded to cache before print)

### Test Android printing in this package

Use the included example app:

1. Install example dependencies:
	- `cd example && yarn install`
2. Run the app on Android:
	- `cd example && yarn android` (or from repo root: `yarn android`)
3. In the example UI:
	- Tap `Discover Printers`
	- Select a discovered printer (if multiple)
	- Tap `Print Test Image`

Notes:

- Ensure your Android device/emulator and printer are reachable on the same network for Wi-Fi discovery.
- For Bluetooth discovery/printing on Android 12+, grant nearby devices/Bluetooth runtime permissions in the app when prompted.



# Contributing

Contributions are very welcome! Please refer to guidelines described in the [contributing guide]( https://github.com/expo/expo#contributing).
