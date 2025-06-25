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

    AsyncFunction("discoverUsb") {promise: Promise ->
      // https://support.brother.com/g/s/es/htmldoc/mobilesdk/guide/discover-printer.html
      // return@Function PrinterSearcher.startUSBSearch(context).channels
      return@Function;
    }

    Function("printImage") { url: String, ipAddress: String ->
      val channel: Channel = Channel.newWifiChannel(ipAddress);
      val result:PrinterDriverGenerateResult = PrinterDriverGenerator.openChannel(channel);
      if (result.getError().getCode() != OpenChannelError.ErrorCode.NoError) {
          Log.e("", "Error - Open Channel: " + result.getError().getCode());
          return@Function;
      }
      val dir = context.getExternalFilesDir(null);
      val printerDriver: PrinterDriver = result.getDriver();
      val printSettings: QLPrintSettings = QLPrintSettings(PrinterModel.QL_810W);

      printSettings.setLabelSize(QLPrintSettings.LabelSize.RollW62RB);
      printSettings.setAutoCut(true);
      printSettings.setWorkPath(dir.toString());

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
