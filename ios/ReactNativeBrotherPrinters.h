// ReactNativeBrotherPrinters.h

#import <React/RCTBridgeModule.h>
#import <BRLMPrinterKit/BRPtouchNetworkManager.h>
#import <BRLMPrinterKit/BRLMPrinterKit.h>
#import <React/RCTEventEmitter.h>

@interface ReactNativeBrotherPrinters : RCTEventEmitter <RCTBridgeModule, BRPtouchNetworkDelegate> {
    NSMutableArray *_brotherDeviceList;
    NSMutableArray *_brotherBluetoothDeviceList;
    BRPtouchNetworkManager    *_networkManager;
    NSString *_imageStr;

    bool hasListeners;
}

@end
