package expo.modules.brotherprinters

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.usb.UsbManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat

import com.brother.sdk.lmprinter.Channel
import com.brother.sdk.lmprinter.Channel.newUsbChannel
import com.brother.sdk.lmprinter.OpenChannelError
import com.brother.sdk.lmprinter.PrintError
import com.brother.sdk.lmprinter.PrinterDriver
import com.brother.sdk.lmprinter.PrinterDriverGenerateResult
import com.brother.sdk.lmprinter.PrinterDriverGenerator
import com.brother.sdk.lmprinter.PrinterModel
import com.brother.sdk.lmprinter.PrinterSearcher
import com.brother.sdk.lmprinter.setting.QLPrintSettings

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition



class ReactNativeBrotherPrintersModule : Module() {

  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ReactNativeBrotherPrinters')` in JavaScript.
    Name("ReactNativeBrotherPrinters")

//    // Sets constant properties on the module. Can take a dictionary or a closure that returns a dictionary.
//    Constants(
//      "PI" to Math.PI
//    )
//
//    // Defines event names that the module can send to JavaScript.
//    Events("onChange")
//
//    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
//    Function("hello") {
//      "Hello world! 👋"
//    }
//
//    // Defines a JavaScript function that always returns a Promise and whose native code
//    // is by default dispatched on the different thread than the JavaScript runtime runs on.
//    AsyncFunction("setValueAsync") { value: String ->
//      // Send an event to JavaScript.
//      sendEvent("onChange", mapOf(
//        "value" to value
//      ))
//    }
//
//    // Enables the module to be used as a native view. Definition components that are accepted as part of
//    // the view definition: Prop, Events.
//    View(ReactNativeBrotherPrintersView::class) {
//      // Defines a setter for the `name` prop.
//      Prop("name") { view: ReactNativeBrotherPrintersView, prop: String ->
//        println(prop)
//      }
//    }


    Function("discover") {
      // https://support.brother.com/g/s/es/htmldoc/mobilesdk/guide/discover-printer.html
      return@Function PrinterSearcher.startUSBSearch(context).channels
    }

    Function("printImage") { base64image: String ->
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
