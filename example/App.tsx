import { StyleSheet, Text, View } from 'react-native';

import * as ReactNativeBrotherPrinters from 'react-native-brother-printers';

export default function App() {
  return (
    <View style={styles.container}>
      <Text>{ReactNativeBrotherPrinters.hello()}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
