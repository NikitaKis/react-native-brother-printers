import { Asset } from "expo-asset";
import { useMemo, useRef, useState } from "react";
import { Button, InteractionManager, PermissionsAndroid, Platform, ScrollView, StyleSheet, Text, View } from "react-native";
import {
  LabelSize,
  discoverBluetoothPrinters,
  printImage,
} from "react-native-brother-printers";
import { captureRef } from "react-native-view-shot";

type DiscoveredPrinter = {
  modelName: string;
  ipAddress?: string;
  serialNumber?: string;
  macAddress?: string;
  printerName?: string;
  location?: string;
  channelType?: string;
  channelId?: string;
};

function isLikelySupportedModel(modelName?: string) {
  const normalized = (modelName ?? "")
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_");

  return (
    normalized.includes("QL_") ||
    normalized.includes("RJ_") ||
    normalized.includes("TD_") ||
    /^QL\d/.test(normalized) ||
    /^RJ\d/.test(normalized) ||
    /^TD\d/.test(normalized)
  );
}

function pickLabelSizeForPrinter(modelName?: string) {
  const normalized = (modelName ?? "")
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_");

  if (normalized.includes("QL_")) {
    // QL devices commonly use 62mm continuous roll media.
    return LabelSize.LabelSizeRollW62;
  }

  return LabelSize.LabelSizeDTRollW90;
}

const TEST_IMAGE_MODULE = require("./assets/icon.png");
const h1 = 80;
const h2 = 60;
const h3 = 50;
const ANDROID_PRINT_TEXT_SCALE = 1;

function StudentPassToPrint({
  firstName,
  lastName,
  actionName,
}: {
  firstName: string;
  lastName: string;
  actionName: string;
}) {
  const textScale = Platform.OS === "android" ? ANDROID_PRINT_TEXT_SCALE : 1;
  const sizeH1 = Math.round(h1 * textScale);
  const sizeH2 = Math.round(h2 * textScale);
  const sizeH3 = Math.round(h3 * textScale);

  const now = new Date();
  const dateTxt = now.toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
  const timeTxt = now.toLocaleTimeString("en-US", {
    hour: "numeric",
    minute: "2-digit",
  });
  const studentName = `${firstName} ${lastName}`.toUpperCase();

  return (
    <View style={styles.passRoot}>
      <Text style={[styles.passText, { fontSize: sizeH2 }]}>{dateTxt}</Text>
      <Text style={[styles.passText, { fontSize: sizeH1, textTransform: "uppercase" }]}>{studentName}</Text>
      <Text style={[styles.passText, { fontSize: sizeH3 }]}>Scanned at <Text style={{ fontSize: sizeH2 }}>{timeTxt}</Text></Text>
      <Text style={[styles.passText, { fontSize: sizeH3 }]}>Front Desk</Text>
      <Text
        style={[
          styles.passText,
          {
            fontSize: sizeH1,
            textTransform: "uppercase",
            color: actionName === "Error" ? "red" : "black",
          },
        ]}
      >
        {actionName || "No action"}
      </Text>
    </View>
  );
}

async function getLocalTestImageUri() {
  const asset = Asset.fromModule(TEST_IMAGE_MODULE);
  if (!asset.localUri) {
    await asset.downloadAsync();
  }

  const uri = asset.localUri ?? asset.uri;
  if (!uri) {
    throw new Error("Unable to resolve local test image URI");
  }

  return uri;
}

async function requestAndroidDiscoveryPermissions() {
  if (Platform.OS !== "android") {
    return true;
  }

  const permissions: string[] = [];

  const permissionMap = PermissionsAndroid.PERMISSIONS as Record<string, string | undefined>;
  const bluetoothScan = permissionMap.BLUETOOTH_SCAN;
  const bluetoothConnect = permissionMap.BLUETOOTH_CONNECT;

  // Android 12+ uses Nearby devices (BLUETOOTH_SCAN / BLUETOOTH_CONNECT).
  if (bluetoothScan && Number(Platform.Version) >= 31) {
    permissions.push(bluetoothScan);
  }

  if (bluetoothConnect && Number(Platform.Version) >= 31) {
    permissions.push(bluetoothConnect);
  }

  if (!permissions.length) {
    return true;
  }

  const missingPermissions: string[] = [];
  for (const permission of permissions) {
    const granted = await PermissionsAndroid.check(permission as any);
    if (!granted) {
      missingPermissions.push(permission);
    }
  }

  if (!missingPermissions.length) {
    return true;
  }

  const results = await PermissionsAndroid.requestMultiple(missingPermissions as any) as Record<string, string>;
  return missingPermissions.every((permission) => {
    const status = results[permission];
    return status === PermissionsAndroid.RESULTS.GRANTED;
  });
}

export default function App() {
  const [printers, setPrinters] = useState<DiscoveredPrinter[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [status, setStatus] = useState("Idle");

  const selectedPrinter = useMemo(() => printers[selectedIndex], [printers, selectedIndex]);
  const isSelectedPrinterSupported = isLikelySupportedModel(selectedPrinter?.modelName);
  const canPrint = !!selectedPrinter && isSelectedPrinterSupported;
  const passViewRef = useRef<View>(null);

  const waitForInteractions = () =>
    new Promise<void>((resolve) => {
      InteractionManager.runAfterInteractions(() => resolve());
    });

  const capturePassImage = async () => {
    await waitForInteractions();
    await new Promise((resolve) => setTimeout(resolve, 120));

    if (!passViewRef.current) {
      throw new Error("Pass view is not ready");
    }

    return captureRef(passViewRef.current, {
      format: "png" as const,
      quality: 0.8,
      width: 680,
      result: "tmpfile" as const,
    });
  };

  const onPressDiscover = async () => {
    setStatus("Discovering Bluetooth printers...");
    try {
      const hasPermissions = await requestAndroidDiscoveryPermissions();
      if (!hasPermissions) {
        setStatus("Bluetooth discovery requires Nearby devices permission. Enable it in Android app settings and try again.");
        return;
      }
      const discovered = await discoverBluetoothPrinters();
      const printerList = Array.isArray(discovered)
        ? discovered
        : discovered
          ? [discovered]
          : [];

      setPrinters(printerList as DiscoveredPrinter[]);
      setSelectedIndex(0);
      const unsupported = (printerList as DiscoveredPrinter[]).filter((printer) => !isLikelySupportedModel(printer.modelName)).length;

      if (unsupported > 0) {
        setStatus(`Discovered ${printerList.length} printer(s). ${unsupported} model(s) are not supported by this SDK.`);
      } else {
        setStatus(`Discovered ${printerList.length} printer(s)`);
      }
    } catch (error) {
      setStatus(`Discovery failed: ${String(error)}`);
    }
  };

  const onPressPrintImage = async () => {
    if (!selectedPrinter) {
      setStatus("No printer selected. Run discovery first.");
      return;
    }

    if (!isSelectedPrinterSupported) {
      setStatus(`Unsupported printer model: ${selectedPrinter.modelName || "unknown"}. This SDK supports QL/RJ/TD families only.`);
      return;
    }

    setStatus("Printing test label...");
    try {
      const localImageUri = await getLocalTestImageUri();
      const labelSize = pickLabelSizeForPrinter(selectedPrinter.modelName);
      await printImage(selectedPrinter, localImageUri, {
        autoCut: true,
        labelSize,
      });
      setStatus("Print command sent successfully");
    } catch (error) {
      setStatus(`Print failed: ${String(error)}`);
    }
  };

  const onPressPrintCapturedPass = async () => {
    if (!selectedPrinter) {
      setStatus("No printer selected. Run discovery first.");
      return;
    }

    if (!isSelectedPrinterSupported) {
      setStatus(`Unsupported printer model: ${selectedPrinter.modelName || "unknown"}. This SDK supports QL/RJ/TD families only.`);
      return;
    }

    setStatus("Capturing student pass and printing...");
    try {
      const captured = await capturePassImage();
      if (!captured) {
        throw new Error("Capture failed");
      }

      const labelSize = pickLabelSizeForPrinter(selectedPrinter.modelName);
      await printImage(selectedPrinter, captured, {
        autoCut: true,
        labelSize,
        trimWhiteMargins: true,
        contentScale: Platform.OS === "android" ? 0.80 : 1,
        contentTopInsetPx: Platform.OS === "android" ? 38 : 0,
      });

      setStatus("Captured image printed successfully");
    } catch (error) {
      setStatus(`Print failed: ${String(error)}`);
    }
  };

  const onPressNextPrinter = () => {
    if (!printers.length) {
      return;
    }

    setSelectedIndex((current) => (current + 1) % printers.length);
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Brother Printer Test</Text>
      <Text style={styles.meta}>Platform: {Platform.OS}</Text>
      <Text style={styles.status}>{status}</Text>
      {!!selectedPrinter && !isSelectedPrinterSupported && (
        <Text style={styles.warning}>
          Selected model {selectedPrinter.modelName || "unknown"} is unsupported by this SDK. Printing is disabled.
        </Text>
      )}

      <View style={styles.actions}>
        <Button title="Discover Bluetooth Printers" onPress={onPressDiscover} />
        <Button title="Print Test Image" onPress={onPressPrintImage} disabled={!canPrint} />
        <Button title="Print Captured Pass" onPress={onPressPrintCapturedPass} disabled={!canPrint} />
        <Button title="Select Next Printer" onPress={onPressNextPrinter} />
      </View>

      <View style={styles.captureSection}>
        <Text style={styles.meta}>Captured pass source</Text>
        <View ref={passViewRef} collapsable={false} style={styles.captureShot}>
          <StudentPassToPrint firstName="John" lastName="Doe" actionName="Test Action" />
        </View>
      </View>

      <View style={styles.list}>
        {printers.length === 0 ? (
          <Text style={styles.item}>No printers discovered yet.</Text>
        ) : (
          printers.map((printer, index) => {
            const isSelected = index === selectedIndex;
            return (
              <View key={`${printer.ipAddress ?? printer.serialNumber}-${index}`} style={styles.itemContainer}>
                <Text style={[styles.item, isSelected && styles.itemSelected]}>
                  {isSelected ? "[selected] " : ""}
                  {printer.modelName || "Unknown model"}
                </Text>
                <Text style={styles.meta}>Name: {printer.printerName || "n/a"}</Text>
                <Text style={styles.meta}>Transport: {printer.channelType || "n/a"}</Text>
                <Text style={styles.meta}>MAC: {printer.macAddress || "n/a"}</Text>
                <Text style={styles.meta}>IP: {printer.ipAddress || "n/a"}</Text>
                <Text style={styles.meta}>Serial: {printer.serialNumber || "n/a"}</Text>
                <Text style={styles.meta}>Location: {printer.location || "n/a"}</Text>
              </View>
            );
          })
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
    gap: 12,
  },
  title: {
    fontSize: 22,
    fontWeight: "700",
  },
  status: {
    fontSize: 14,
  },
  warning: {
    color: "#8a4b00",
    fontWeight: "600",
  },
  actions: {
    gap: 10,
    marginTop: 6,
  },
  list: {
    marginTop: 8,
    gap: 10,
  },
  captureSection: {
    marginTop: 8,
    padding: 8,
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 10,
    gap: 8,
    backgroundColor: "#f9f9f9",
  },
  captureShot: {
    alignItems: "center",
  },
  itemContainer: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 10,
    padding: 10,
  },
  item: {
    fontSize: 15,
  },
  itemSelected: {
    color: "#0d6b2f",
    fontWeight: "600",
  },
  meta: {
    color: "#555",
  },
  passRoot: {
    justifyContent: "flex-start",
    alignItems: "center",
    width: 700,
    minHeight: 700,
    paddingTop: 0,
    paddingBottom: 10,
    gap: 8,
    backgroundColor: "white",
  },
  passText: {
    textAlign: "center",
  },
});
