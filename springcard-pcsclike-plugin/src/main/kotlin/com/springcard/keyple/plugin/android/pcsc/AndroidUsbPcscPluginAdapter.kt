/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.springcard.keyple.plugin.R
import com.springcard.keyple.plugin.android.pcsc.spi.DeviceScannerSpi
import com.springcard.pcsclike.SCardReaderList
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber

/**
 * Provides the specific means to manage USB devices.
 * @since 1.0.0
 */
internal class AndroidUsbPcscPluginAdapter(name: String, context: Context) :
    AbstractAndroidPcscPluginAdapter(name, context) {
  private lateinit var usbAttachReceiver: BroadcastReceiver
  private val usbDeviceList: MutableMap<String, UsbDevice> = mutableMapOf()
  private val usbDeviceInfoMap: MutableMap<String, DeviceInfo> = mutableMapOf()
  private lateinit var deviceScannerSpi: DeviceScannerSpi
  private val deviceFilter = mutableListOf<String>()
  private val handler: Handler = Handler(Looper.getMainLooper())
  private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

  private val usbManager: UsbManager by lazy {
    context.getSystemService(Context.USB_SERVICE) as UsbManager
  }

  private val permissionIntent: PendingIntent by lazy {
    PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
  }

  init {
    /* Parse device_filter.xml */
    val xmlResourceParser = context!!.resources.getXml(R.xml.device_filter)
    var eventType = xmlResourceParser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          if (xmlResourceParser.name == "usb-device") {
            val vid = xmlResourceParser.getAttributeIntValue(1, 0)
            val pid = xmlResourceParser.getAttributeIntValue(0, 0)
            if (pid != 0 && vid != 0) {
              deviceFilter.add(getDeviceIdsAsString(vid, pid))
            }
          }
        }
      }
      eventType = xmlResourceParser.next()
    }
    /* Scan already present USB devices */
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
    deviceList.values.forEach { device -> addDevice(device) }
  }

  /** Specific scanning for USB devices. */
  override fun scanDevices(
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      deviceScannerSpi: DeviceScannerSpi
  ) {
    this.deviceScannerSpi = deviceScannerSpi
    context.packageManager.takeIf { !it.hasSystemFeature(PackageManager.FEATURE_USB_HOST) }?.also {
      Toast.makeText(context, R.string.usb_host_not_supported, Toast.LENGTH_SHORT).show()
    }
    handler.postDelayed(notifyScanResults, timeout * 1000)
    usbAttachReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            when {
              UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action -> {
                if (usbDevice != null && addDevice(usbDevice) && stopOnFirstDeviceDiscovered) {
                  // shorten timer
                  handler.removeCallbacks(notifyScanResults)
                  handler.postDelayed(notifyScanResults, 0)
                } else {
                  Timber.d("Device is not in device filter list (not a SpringCard device?)")
                }
              }
              UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action -> {
                usbDeviceList.remove(usbDevice!!.deviceId.toString())
              }
              ACTION_USB_PERMISSION == intent.action -> {
                synchronized(this) {
                  if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Timber.d("Permission granted for device %s", usbDevice)
                    SCardReaderList.create(context, usbDevice!!, scardCallbacks)
                  } else {
                    Timber.d("Permission denied for device %s", usbDevice)
                  }
                }
              }
            }
          }
        }
    context.registerReceiver(usbAttachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
    context.registerReceiver(usbAttachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
    context.registerReceiver(usbAttachReceiver, IntentFilter(ACTION_USB_PERMISSION))
  }

  /**
   * Specific USB device connection
   * @param identifier The USB device identifier
   */
  override fun connectToDevice(identifier: String) {
    Timber.d("Connection to USB device: %s", identifier)
    val usbDevice = usbDeviceList[identifier]
    if (usbDevice != null) {
      if (usbManager.hasPermission(usbDevice)) {
        // permission is already granted, let's create the SCardReaderList
        SCardReaderList.create(context, usbDevice, scardCallbacks)
      } else {
        // request permission
        // the creation of the SCardReaderList is postponed until the confirmation of the grant is
        // received.
        usbManager.requestPermission(usbDevice, permissionIntent)
      }
    } else {
      throw IllegalStateException("USB device with identifier $identifier not found")
    }
  }

  /**
   * (private) Adds the device to the device list and to the device information list map if it
   * matches the VID/PID based filter.
   * @param usbDevice The USB device to add.
   * @return True if the device was added, false if not.
   */
  private fun addDevice(usbDevice: UsbDevice): Boolean {
    val deviceIdAsString = getDeviceIdsAsString(usbDevice.vendorId, usbDevice.productId)
    if (deviceFilter.contains(deviceIdAsString)) {
      val usbDeviceInfo =
          DeviceInfo(
              usbDevice.deviceId.toString(),
              "${usbDevice.manufacturerName} ${usbDevice.productName}",
              "NAME:${usbDevice.deviceName}, VID:${intTo4hex(usbDevice.vendorId)}, PID:${intTo4hex(usbDevice.productId)}")
      if (!usbDeviceInfoMap.containsKey(usbDeviceInfo.identifier)) {
        Timber.d("BLE device added: %s", usbDeviceInfo)
        usbDeviceInfoMap[usbDeviceInfo.identifier] = usbDeviceInfo
        usbDeviceList[usbDeviceInfo.identifier] = usbDevice
        return true
      }
    }
    return false
  }

  /**
   * (private) Returns a string containing the provided USB identifiers (VID/PID).
   * @param vid The vendor Id.
   * @param pid The product Id.
   * @return A not empty String.
   */
  private fun getDeviceIdsAsString(vid: Int, pid: Int): String {
    return "USB\\VID=${intTo4hex(vid)}&PID=${intTo4hex(pid)}"
  }

  /**
   * Helper to convert a short int into hex (must be <65536)
   * @return A 4-byte hex string.
   */
  private fun intTo4hex(value: Int): String {
    return value.toString(16).toUpperCase().padStart(2, '0')
  }

  /** Device discovery notifier */
  private val notifyScanResults =
      Runnable {
        Timber.d(
            "Notifying USB scan results (%d device(s) found), stop USB scanning.",
            usbDeviceInfoMap.size)
        deviceScannerSpi.onDeviceDiscovered(usbDeviceInfoMap.values)
      }
}
