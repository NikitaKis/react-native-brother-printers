package expo.modules.brotherprinters

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Base64
import android.util.Log
import java.io.FileOutputStream
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.brother.sdk.lmprinter.Channel
import com.brother.sdk.lmprinter.OpenChannelError
import com.brother.sdk.lmprinter.PrintError
import com.brother.sdk.lmprinter.PrinterDriver
import com.brother.sdk.lmprinter.PrinterDriverGenerateResult
import com.brother.sdk.lmprinter.PrinterDriverGenerator
import com.brother.sdk.lmprinter.PrinterModel
import com.brother.sdk.lmprinter.PrinterSearchError
import com.brother.sdk.lmprinter.PrinterSearcher
import com.brother.sdk.lmprinter.NetworkSearchOption
import com.brother.sdk.lmprinter.BLESearchOption
import com.brother.sdk.lmprinter.setting.PrintImageSettings
import com.brother.sdk.lmprinter.setting.QLPrintSettings

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ReactNativeBrotherPrintersModule : Module() {

  private val discoveredChannels = ConcurrentHashMap<String, Channel>()

  private fun isMacAddress(value: String): Boolean {
    val trimmed = value.trim()
    return Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$").matches(trimmed)
  }

  private fun serializeChannel(channel: Channel): Map<String, String> {
    val channelId = UUID.randomUUID().toString()
    discoveredChannels[channelId] = channel
    val modelName = channel.extraInfo[Channel.ExtraInfoKey.ModelName] ?: ""
    val serialNumber = channel.extraInfo[Channel.ExtraInfoKey.SerialNubmer] ?: ""
    val macAddress = channel.extraInfo[Channel.ExtraInfoKey.MACAddress] ?: ""
    val printerName = channel.extraInfo[Channel.ExtraInfoKey.NodeName] ?: modelName
    val location = channel.extraInfo[Channel.ExtraInfoKey.Location] ?: ""
    val channelInfo = channel.channelInfo ?: ""
    val ipAddress = when (channel.channelType) {
      Channel.ChannelType.Bluetooth,
      Channel.ChannelType.BluetoothLowEnergy -> macAddress.ifBlank { channelInfo }
      else -> channelInfo
    }

    return mapOf(
      "modelName" to modelName,
      "printerName" to printerName,
      "ipAddress" to ipAddress,
      "serialNumber" to serialNumber,
      "macAddress" to macAddress,
      "location" to location,
      "channelId" to channelId,
      "channelType" to channel.channelType.name
    )
  }

  private fun mergeUniquePrinters(
    target: MutableList<Map<String, String>>,
    seen: MutableSet<String>,
    channels: List<Channel>
  ) {
    for (channel in channels) {
      val serialized = serializeChannel(channel)
      val key = listOf(
        serialized["ipAddress"],
        serialized["serialNumber"],
        serialized["modelName"]
      ).joinToString("|")

      if (seen.add(key)) {
        target.add(serialized)
      }
    }
  }

  private fun <T> withMulticastLock(block: () -> T): T {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val lock = wifiManager?.createMulticastLock("brother-printer-discovery")
    lock?.setReferenceCounted(true)

    return try {
      lock?.acquire()
      block()
    } finally {
      if (lock?.isHeld == true) {
        lock.release()
      }
    }
  }

  private fun discoverNetworkPrinters(
    timeoutSeconds: Double = 15.0,
    tethering: Boolean = false
  ): List<Map<String, String>> {
    discoveredChannels.clear()
    val foundPrinters = mutableListOf<Map<String, String>>()
    val seenKeys = mutableSetOf<String>()
    val option = NetworkSearchOption(timeoutSeconds, tethering)

    val result = withMulticastLock {
      PrinterSearcher.startNetworkSearch(context, option) { channel ->
        mergeUniquePrinters(foundPrinters, seenKeys, listOf(channel))
      }
    }

    val error = result.error
    if (error.code != PrinterSearchError.ErrorCode.NoError) {
      throw Exception("Network discovery failed: ${error.code}")
    }

    mergeUniquePrinters(foundPrinters, seenKeys, result.channels ?: emptyList())
    return foundPrinters
  }

  private fun discoverBluetoothPrinters(): List<Map<String, String>> {
    discoveredChannels.clear()
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
      ?: throw Exception("Bluetooth adapter is unavailable on this device")
    if (!bluetoothAdapter.isEnabled) {
      throw Exception("Bluetooth is disabled. Enable Bluetooth and try again.")
    }

    val foundPrinters = mutableListOf<Map<String, String>>()
    val seenKeys = mutableSetOf<String>()

    val bluetoothResult = PrinterSearcher.startBluetoothSearch(context)
    val bluetoothError = bluetoothResult.error
    if (bluetoothError.code == PrinterSearchError.ErrorCode.NoError) {
      mergeUniquePrinters(foundPrinters, seenKeys, bluetoothResult.channels ?: emptyList())
    }

    val bleResult = PrinterSearcher.startBLESearch(context, BLESearchOption(8.0)) { channel ->
      mergeUniquePrinters(foundPrinters, seenKeys, listOf(channel))
    }
    val bleError = bleResult.error
    if (bleError.code == PrinterSearchError.ErrorCode.NoError) {
      mergeUniquePrinters(foundPrinters, seenKeys, bleResult.channels ?: emptyList())
    }

    if (foundPrinters.isNotEmpty()) {
      return foundPrinters
    }

    val pairedBrotherDevices = bluetoothAdapter.bondedDevices
      ?.mapNotNull { device ->
        val deviceName = device.name ?: return@mapNotNull null
        if (!deviceName.contains("BROTHER", ignoreCase = true) &&
          !deviceName.contains("QL", ignoreCase = true)
        ) {
          return@mapNotNull null
        }

        mapOf(
          "modelName" to deviceName,
          "printerName" to deviceName,
          "ipAddress" to device.address,
          "serialNumber" to device.address,
          "macAddress" to device.address,
          "location" to "",
          "channelId" to "",
          "channelType" to Channel.ChannelType.Bluetooth.name
        )
      }
      ?: emptyList()

    if (pairedBrotherDevices.isNotEmpty()) {
      return pairedBrotherDevices
    }

    if (bleError.code != PrinterSearchError.ErrorCode.NoError) {
      throw Exception("Bluetooth discovery failed: classic=${bluetoothError.code}, ble=${bleError.code}")
    }

    if (bluetoothError.code != PrinterSearchError.ErrorCode.NoError) {
      throw Exception("Bluetooth discovery failed: ${bluetoothError.code}")
    }

    return emptyList()
  }

  private fun discoverUsbPrinters(): List<Map<String, String>> {
    discoveredChannels.clear()
    val result = PrinterSearcher.startUSBSearch(context)
    val error = result.error
    if (error.code != PrinterSearchError.ErrorCode.NoError) {
      throw Exception("USB discovery failed: ${error.code}")
    }

    return result.channels?.map { serializeChannel(it) } ?: emptyList()
  }

  private fun mapLabelSize(labelSizeValue: Int): QLPrintSettings.LabelSize {
    return when (labelSizeValue) {
      0 -> QLPrintSettings.LabelSize.DieCutW17H54
      1 -> QLPrintSettings.LabelSize.DieCutW17H87
      2 -> QLPrintSettings.LabelSize.DieCutW23H23
      3 -> QLPrintSettings.LabelSize.DieCutW29H42
      4 -> QLPrintSettings.LabelSize.DieCutW29H90
      5 -> QLPrintSettings.LabelSize.DieCutW38H90
      6 -> QLPrintSettings.LabelSize.DieCutW39H48
      7 -> QLPrintSettings.LabelSize.DieCutW52H29
      8 -> QLPrintSettings.LabelSize.DieCutW62H29
      9 -> QLPrintSettings.LabelSize.DieCutW62H100
      10 -> QLPrintSettings.LabelSize.DieCutW60H86
      11 -> QLPrintSettings.LabelSize.DieCutW54H29
      12 -> QLPrintSettings.LabelSize.DieCutW102H51
      13 -> QLPrintSettings.LabelSize.DieCutW102H152
      14 -> QLPrintSettings.LabelSize.RollW12
      15 -> QLPrintSettings.LabelSize.RollW29
      16 -> QLPrintSettings.LabelSize.RollW38
      17 -> QLPrintSettings.LabelSize.RollW50
      18 -> QLPrintSettings.LabelSize.RollW54
      19 -> QLPrintSettings.LabelSize.RollW62
      20 -> QLPrintSettings.LabelSize.RollW62RB
      21 -> QLPrintSettings.LabelSize.RollW102
      22 -> QLPrintSettings.LabelSize.RollW103
      23 -> QLPrintSettings.LabelSize.DTRollW90
      24 -> QLPrintSettings.LabelSize.DTRollW102
      25 -> QLPrintSettings.LabelSize.DTRollW102H51
      26 -> QLPrintSettings.LabelSize.DTRollW102H152
      else -> QLPrintSettings.LabelSize.RollW62RB
    }
  }

  private fun resolvePrinterModel(modelName: String?): PrinterModel {
    val normalizedModel = modelName
      ?.uppercase()
      ?.replace('-', '_')
      ?.replace(' ', '_')
      ?.replace('/', '_')
      ?: ""

    return try {
      if (normalizedModel.isBlank()) PrinterModel.QL_810W else PrinterModel.valueOf(normalizedModel)
    } catch (_: IllegalArgumentException) {
      PrinterModel.QL_810W
    }
  }

  private fun requireSupportedPrinterModel(modelName: String?): PrinterModel {
    val normalizedModel = modelName
      ?.uppercase()
      ?.replace(Regex("[^A-Z0-9]+"), "_")
      ?.trim('_')
      ?: ""
    val compactModel = normalizedModel.replace("_", "")

    if (normalizedModel.isBlank()) {
      throw Exception("Printer model is missing. This Android SDK only supports specific Brother QL/RJ/TD models.")
    }

    if (normalizedModel.contains("ZSB")) {
      throw Exception("Unsupported printer model: $modelName. This Android Brother SDK build supports specific QL/RJ/TD models and does not include the ZSB family.")
    }

    for (supportedModel in PrinterModel.values()) {
      val enumName = supportedModel.name
      val compactEnumName = enumName.replace("_", "")

      if (
        normalizedModel == enumName ||
        normalizedModel.contains(enumName) ||
        compactModel == compactEnumName ||
        compactModel.contains(compactEnumName)
      ) {
        return supportedModel
      }
    }

    throw Exception("Unsupported printer model: $modelName. This Android Brother SDK build supports specific QL/RJ/TD models and does not include this printer family.")
  }

  private fun resolvePrintablePath(uri: String): String {
    if (uri.startsWith("data:image/") && uri.contains(";base64,")) {
      val base64Payload = uri.substringAfter(";base64,", "")
      val imageBytes = try {
        Base64.decode(base64Payload, Base64.DEFAULT)
      } catch (_: IllegalArgumentException) {
        null
      }

      if (imageBytes != null) {
        val destination = File(context.cacheDir, "brother-print-base64-${System.currentTimeMillis()}.png")
        FileOutputStream(destination).use { output ->
          output.write(imageBytes)
        }
        return destination.absolutePath
      }
    }

    if (uri.startsWith("http://") || uri.startsWith("https://")) {
      val extension = uri.substringAfterLast('.', "png").substringBefore('?')
      val destination = File(context.cacheDir, "brother-print-${System.currentTimeMillis()}.$extension")
      URL(uri).openStream().use { input ->
        FileOutputStream(destination).use { output ->
          input.copyTo(output)
        }
      }
      return destination.absolutePath
    }

    if (uri.startsWith("content://")) {
      val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
        ?: throw Exception("Unable to open content URI: $uri")
      val destination = File(context.cacheDir, "brother-print-content-${System.currentTimeMillis()}.png")

      inputStream.use { input ->
        FileOutputStream(destination).use { output ->
          input.copyTo(output)
        }
      }

      return destination.absolutePath
    }

    if (uri.startsWith("file://")) {
      return Uri.parse(uri).path ?: uri.removePrefix("file://")
    }

    // Support ViewShot output when `result: 'base64'` is used.
    if (!uri.contains("://") && !uri.startsWith("/")) {
      val sanitized = uri.trim()
      val looksLikeBase64 = sanitized.length > 100 &&
        sanitized.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))

      if (looksLikeBase64) {
        val imageBytes = try {
          Base64.decode(sanitized, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
          null
        }

        if (imageBytes != null) {
          val destination = File(context.cacheDir, "brother-print-base64-raw-${System.currentTimeMillis()}.png")
          FileOutputStream(destination).use { output ->
            output.write(imageBytes)
          }
          return destination.absolutePath
        }
      }
    }

    return uri
  }

  private fun decodeBitmap(uri: String, printablePath: String) = when {
    uri.startsWith("data:image/") && uri.contains(";base64,") -> {
      val base64Payload = uri.substringAfter(";base64,", "")
      val imageBytes = try {
        Base64.decode(base64Payload, Base64.DEFAULT)
      } catch (_: IllegalArgumentException) {
        null
      }

      imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    uri.startsWith("http://") || uri.startsWith("https://") -> {
      URL(uri).openStream().use { input ->
        BitmapFactory.decodeStream(input)
      }
    }

    uri.startsWith("content://") -> {
      context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
        BitmapFactory.decodeStream(input)
      }
    }

    !uri.contains("://") && !uri.startsWith("/") -> {
      val sanitized = uri.trim()
      val looksLikeBase64 = sanitized.length > 100 &&
        sanitized.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))

      if (looksLikeBase64) {
        val imageBytes = try {
          Base64.decode(sanitized, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
          null
        }

        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
      } else {
        BitmapFactory.decodeFile(printablePath)
      }
    }

    else -> BitmapFactory.decodeFile(printablePath)
  }

  private fun ensureWhiteBackground(bitmap: Bitmap): Bitmap {
    if (bitmap.config == Bitmap.Config.RGB_565 || !bitmap.hasAlpha()) {
      return bitmap
    }

    val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(flattened)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    return flattened
  }

  private fun trimWhiteMargins(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) {
      return bitmap
    }

    var left = width
    var top = height
    var right = -1
    var bottom = -1

    // Treat near-white pixels as background so anti-aliased edges are preserved.
    val threshold = 245
    for (y in 0 until height) {
      for (x in 0 until width) {
        val color = bitmap.getPixel(x, y)
        val alpha = Color.alpha(color)
        if (alpha == 0) continue

        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val isForeground = red < threshold || green < threshold || blue < threshold
        if (!isForeground) continue

        if (x < left) left = x
        if (y < top) top = y
        if (x > right) right = x
        if (y > bottom) bottom = y
      }
    }

    if (right < left || bottom < top) {
      return bitmap
    }

    val padding = 8
    val cropLeft = (left - padding).coerceAtLeast(0)
    val cropTop = (top - padding).coerceAtLeast(0)
    val cropRight = (right + padding).coerceAtMost(width - 1)
    val cropBottom = (bottom + padding).coerceAtMost(height - 1)
    val cropWidth = cropRight - cropLeft + 1
    val cropHeight = cropBottom - cropTop + 1

    if (cropWidth <= 0 || cropHeight <= 0 || (cropWidth == width && cropHeight == height)) {
      return bitmap
    }

    return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
  }

  private fun applyContentScale(bitmap: Bitmap, contentScale: Float, topInsetPx: Int): Bitmap {
    val safeScale = contentScale.coerceIn(0.5f, 1.0f)
    val safeTopInsetPx = topInsetPx.coerceAtLeast(0)
    if (safeScale >= 0.999f && safeTopInsetPx == 0) {
      return bitmap
    }

    val srcWidth = bitmap.width
    val srcHeight = bitmap.height
    if (srcWidth <= 1 || srcHeight <= 1) {
      return bitmap
    }

    val targetWidth = (srcWidth * safeScale).toInt().coerceAtLeast(1)
    val maxTargetHeight = (srcHeight - safeTopInsetPx).coerceAtLeast(1)
    val targetHeight = (srcHeight * safeScale).toInt().coerceAtLeast(1).coerceAtMost(maxTargetHeight)
    val left = ((srcWidth - targetWidth) / 2f).coerceAtLeast(0f)
    val top = safeTopInsetPx.toFloat().coerceAtMost((srcHeight - 1).toFloat())
    val right = (left + targetWidth).coerceAtMost(srcWidth.toFloat())
    val bottom = (top + targetHeight).coerceAtMost(srcHeight.toFloat())

    val output = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.WHITE)
    val destination = RectF(left, top, right, bottom)
    canvas.drawBitmap(bitmap, null, destination, null)
    return output
  }

  private fun buildChannel(device: Map<String, Any?>): Channel {
    val channelId = (device["channelId"] as? String)?.trim()
    if (!channelId.isNullOrBlank()) {
      discoveredChannels[channelId]?.let { return it }
    }

    val ipAddress = (device["ipAddress"] as? String)?.trim()
    val serialNumber = (device["serialNumber"] as? String)?.trim()
    val macAddress = (device["macAddress"] as? String)?.trim()
    val channelType = (device["channelType"] as? String)?.trim()
    val bluetoothAddress = when {
      !macAddress.isNullOrBlank() -> macAddress
      !serialNumber.isNullOrBlank() -> serialNumber
      !ipAddress.isNullOrBlank() && isMacAddress(ipAddress) -> ipAddress
      else -> null
    }

    if (!bluetoothAddress.isNullOrBlank()) {
      val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        ?: throw Exception("Bluetooth adapter is unavailable")
      if (channelType == Channel.ChannelType.BluetoothLowEnergy.name) {
        return Channel.newBluetoothLowEnergyChannel(bluetoothAddress, context, bluetoothAdapter)
      }
      return Channel.newBluetoothChannel(bluetoothAddress, bluetoothAdapter)
    }

    if (!ipAddress.isNullOrBlank()) {
      return Channel.newWifiChannel(ipAddress)
    }

    throw Exception("Either ipAddress or serialNumber is required")
  }

  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ReactNativeBrotherPrinters')` in JavaScript.
    Name("ReactNativeBrotherPrinters")

    AsyncFunction("discover") { options: Map<String, Any?>? ->
      val timeoutSeconds = (options?.get("timeoutSeconds") as? Number)?.toDouble() ?: 15.0
      val tethering = (options?.get("tethering") as? Boolean) ?: false
      discoverNetworkPrinters(timeoutSeconds, tethering)
    }

    AsyncFunction("discoverPrinters") { options: Map<String, Any?>? ->
      val timeoutSeconds = (options?.get("timeoutSeconds") as? Number)?.toDouble() ?: 15.0
      val tethering = (options?.get("tethering") as? Boolean) ?: false
      discoverNetworkPrinters(timeoutSeconds, tethering)
    }

    AsyncFunction("discoverPrintersUsb") {
      discoverUsbPrinters()
    }

    AsyncFunction("discoverBluetoothPrinters") {
      discoverBluetoothPrinters()
    }

    AsyncFunction("pingPrinter") { ipAddress: String ->
      val channel = Channel.newWifiChannel(ipAddress)
      val result = PrinterDriverGenerator.openChannel(channel)
      if (result.error.code != OpenChannelError.ErrorCode.NoError || result.driver == null) {
        throw Exception("Unable to connect to printer: ${result.error.code}")
      }

      result.driver.closeChannel()
      mapOf("reachable" to true)
    }

    AsyncFunction("printImage") { device: Map<String, Any?>, uri: String, options: Map<String, Any?>? ->
      val modelName = device["modelName"] as? String
      val model = requireSupportedPrinterModel(modelName)
      val channel = buildChannel(device)
      val result: PrinterDriverGenerateResult = PrinterDriverGenerator.openChannel(channel)
      if (result.error.code != OpenChannelError.ErrorCode.NoError || result.driver == null) {
        throw Exception("Open channel failed: ${result.error.code}")
      }

      val printerDriver: PrinterDriver = result.driver
      try {
        val printSettings = QLPrintSettings(model)
        val labelSizeValue = (options?.get("labelSize") as? Number)?.toInt()
        val autoCut = (options?.get("autoCut") as? Boolean) ?: true
        val workDir = context.getExternalFilesDir(null) ?: context.cacheDir
        val printablePath = resolvePrintablePath(uri)

        if (labelSizeValue != null) {
          try {
            printSettings.setLabelSize(mapLabelSize(labelSizeValue))
          } catch (error: Throwable) {
            throw Exception("Set LabelSize Error: labelSize=$labelSizeValue model=$modelName. Ensure selected label size matches loaded media.")
          }
        }
        printSettings.setScaleMode(PrintImageSettings.ScaleMode.FitPaperAspect)
        printSettings.setPrintOrientation(PrintImageSettings.Orientation.Portrait)
        printSettings.setHAlignment(PrintImageSettings.HorizontalAlignment.Center)
        printSettings.setVAlignment(PrintImageSettings.VerticalAlignment.Center)
        printSettings.setAutoCut(autoCut)
        printSettings.setWorkPath(workDir.absolutePath)

        // Prefer bitmap mode to avoid stream-opening failures on URI/file schemes.
        val trimMargins = (options?.get("trimWhiteMargins") as? Boolean) ?: true
        val contentScale = ((options?.get("contentScale") as? Number)?.toDouble()?.toFloat() ?: 0.95f)
          .coerceIn(0.5f, 1.0f)
        val contentTopInsetPx = ((options?.get("contentTopInsetPx") as? Number)?.toInt() ?: 0)
          .coerceAtLeast(0)
        val bitmap = decodeBitmap(uri, printablePath)
          ?.let { ensureWhiteBackground(it) }
          ?.let { if (trimMargins) trimWhiteMargins(it) else it }
          ?.let { applyContentScale(it, contentScale, contentTopInsetPx) }
        val printError: PrintError = if (bitmap != null) {
          printerDriver.printImage(bitmap, printSettings)
        } else {
          printerDriver.printImage(printablePath, printSettings)
        }

        if (printError.code != PrintError.ErrorCode.NoError) {
          throw Exception("Print failed: ${printError.code} (uri=$uri, path=$printablePath, bitmapDecoded=${bitmap != null})")
        }

        mapOf("success" to true)
      } finally {
        printerDriver.closeChannel()
      }
    }

  }

  private val context
  get() = requireNotNull(appContext.reactContext)

}
