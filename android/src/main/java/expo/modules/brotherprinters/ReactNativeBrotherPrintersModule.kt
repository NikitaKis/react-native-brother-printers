package expo.modules.brotherprinters

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.usb.UsbManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

import com.brother.sdk.lmprinter.Channel
import com.brother.sdk.lmprinter.Channel.newUsbChannel
import com.brother.sdk.lmprinter.OpenChannelError
import com.brother.sdk.lmprinter.PrintError
import com.brother.sdk.lmprinter.PrinterDriver
import com.brother.sdk.lmprinter.PrinterDriverGenerateResult
import com.brother.sdk.lmprinter.PrinterDriverGenerator
import com.brother.sdk.lmprinter.PrinterModel
import com.brother.sdk.lmprinter.PrinterSearcher
import com.brother.sdk.lmprinter.NetworkSearchOption
import com.brother.sdk.lmprinter.setting.QLPrintSettings

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise

class ReactNativeBrotherPrintersModule : Module() {

  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ReactNativeBrotherPrinters')` in JavaScript.
    Name("ReactNativeBrotherPrinters")

    AsyncFunction("discover") {promise: Promise ->
      // https://support.brother.com/g/s/es/htmldoc/mobilesdk/guide/discover-printer.html
      // return@Function PrinterSearcher.startUSBSearch(context).channels
      val option = NetworkSearchOption(15.0, false)
      val result = PrinterSearcher.startNetworkSearch(context, option){ channel ->
          val modelName = channel.extraInfo[Channel.ExtraInfoKey.ModelName] ?: ""
          val ipaddress = channel.channelInfo
          Log.d("TAG", "Model : $modelName, IP Address: $ipaddress")
          promise.resolve(mapOf(
            "model" to modelName,
            "ipAddress" to ipaddress
          ))
      }
    }

    Function("printImage") { url: String ->
      /*
      // https://support.brother.com/g/s/es/htmldoc/mobilesdk/guide/print-image.html
      // https://support.brother.com/g/s/es/htmldoc/mobilesdk/reference/android_v4/channel.html#newusbchannel
      // https://github.dev/thiendangit/react-native-thermal-receipt-printer-image-qr
          // USBPrinterAdapter.java:109
      val usbManager : UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
      // TODO check usbManager, use second prop to select proper printer

      val channel: Channel = newUsbChannel(usbManager)

      val result:PrinterDriverGenerateResult = PrinterDriverGenerator.openChannel(channel)
      if (result.error.code != OpenChannelError.ErrorCode.NoError) {
        Log.e("", "Error - Open Channel: " + result.error.code)
        return@Function
      }

      val printerDriver:PrinterDriver = result.driver
      val printSettings = QLPrintSettings(PrinterModel.QL_800)
      // TODO check if required
      // printSettings.labelSize = QLPrintSettings.LabelSize.;
      printSettings.isAutoCut = true
      // TODO check
      // printSettings.workPath = dir.toString();

      val imageBytes = Base64.decode(base64image, 0)
      val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

      val printError: PrintError = printerDriver.printImage(image, printSettings)

      if (printError.code != PrintError.ErrorCode.NoError) {
        Log.d("", "Error - Print Image: " + printError.code)
      }
      else {
        Log.d("", "Success - Print Image")
      }
    */


      val result:PrinterDriverGenerateResult = PrinterDriverGenerator.openChannel(channel);
      if (result.getError().getCode() != OpenChannelError.ErrorCode.NoError) {
          Log.e("", "Error - Open Channel: " + result.getError().getCode());
          return@Function;
      }

      val appSpecificExternalDir = File(context.getExternalFilesDir(null), url)
      Log.d("", "appSpecificExternalDir - " + appSpecificExternalDir);
      //val file:File = new File(url);

      val printerDriver: PrinterDriver = result.getDriver();
      val printSettings: QLPrintSettings = QLPrintSettings(PrinterModel.QL_810W);

      printSettings.setLabelSize(QLPrintSettings.LabelSize.RollW62RB);
      printSettings.setAutoCut(true);
      printSettings.setWorkPath(appSpecificExternalDir.toString());
      Log.d("", "url - " + url);
      val printError: PrintError = printerDriver.printImage(url, printSettings);

      if (printError.getCode() != PrintError.ErrorCode.NoError) {
          Log.d("", "Error - Print Image: " + printError.getCode());
      }
      else {
          Log.d("", "Success - Print Image");
      }
      printerDriver.closeChannel();
      return@Function
    }

    Function("getPaperSize") {
      "unimplemented getPaperSize called"
    }
    Function("openCashDrawer") {
      "unimplemented openCashDrawer called"
    }



  }

  private val context
  get() = requireNotNull(appContext.reactContext)

}
