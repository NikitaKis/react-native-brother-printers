import { Button, StyleSheet, Text, View } from "react-native";
import {
  discover,
  printImage,
  getPaperSize,
  openCashDrawer,
} from "react-native-brother-printers";

export default function App() {
  const onPressDiscover = () => {
    discover();
  };
  const onPressPrintImage = () => {
    printImage("");
  };
  const onPressGetPaperSize = () => {
    getPaperSize();
  };
  const onPressOpenCashDrawer = () => {
    openCashDrawer();
  };

  return (
    <View style={styles.container}>
      <Text>Test actions</Text>
      <Button title="Discover" onPress={onPressDiscover} />
      <Button title="printImage" onPress={onPressPrintImage} />
      <Button title="getPaperSize" onPress={onPressGetPaperSize} />
      <Button title="openCashDrawer" onPress={onPressOpenCashDrawer} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    alignItems: "center",
    justifyContent: "center",
  },
});
