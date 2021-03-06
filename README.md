# Keyple Plugin SpringCard Android PC/SC

[![Kotlin](https://img.shields.io/badge/kotlin-1.4.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![KDoc link](https://img.shields.io/badge/API_reference-KDoc-blue)](https://jeanpierrefortune.github.io/keyple-plugin-springcard-android-pcsc-lib/)

## Overview

The **Keyple Plugin SpringCard Android PC/SC** is an add-on to allow an application using Keyple to interact with [Springcard](https://www.springcard.com) PC/SC readers in Android.

The physical link can be either USB or Bluetooth Low Energy.

## Setup

Here are the necessary dependencies to declare in your project to integrate this library:

### Gradle Groovy
```groovy
implementation 'org.calypsonet.terminal:calypsonet-terminal-reader-java-api:1.0.+'
implementation 'org.eclipse.keyple:keyple-common-java-api:2.0.+'
implementation 'org.eclipse.keyple:keyple-util-java-lib:2.+'
implementation 'org.eclipse.keyple:keyple-service-java-lib:2.0.1'
implementation 'com.springcard.keyple:keyple-plugin-springcard-android-pcsc-lib:1.0.0'
```

### Gradle Kotlin
```kotlin
implementation("org.calypsonet.terminal:calypsonet-terminal-reader-java-api:1.0.+")
implementation("org.eclipse.keyple:keyple-common-java-api:2.0.+")
implementation("org.eclipse.keyple:keyple-util-java-lib:2.+")
implementation("org.eclipse.keyple:keyple-service-java-lib:2.0.1")
implementation("com.springcard.keyple:keyple-plugin-springcard-android-pcsc-lib:1.0.0")
```

### Maven
```mvn
<dependency>
  <groupId>org.calypsonet.terminal</groupId>
  <artifactId>calypsonet-terminal-reader-java-api</artifactId>
  <version>[1.0.0,1.1.0)</version>
</dependency>
<dependency>
  <groupId>org.eclipse.keyple</groupId>
  <artifactId>keyple-common-java-api</artifactId>
  <version>[2.0.0,2.1.0)</version>
</dependency>
<dependency>
  <groupId>org.eclipse.keyple</groupId>
  <artifactId>keyple-util-java-lib</artifactId>
  <version>[2.0.0,3.0.0)</version>
</dependency>
<dependency>
  <groupId>org.eclipse.keyple</groupId>
  <artifactId>keyple-service-java-lib</artifactId>
  <version>2.0.1</version>
</dependency>
<dependency>
  <groupId>com.springcard.keyple</groupId>
  <artifactId>keyple-plugin-springcard-android-pcsc-lib</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Initialization of the plugin
The first thing to do to use this plugin is to get a factory with [AndroidPcscPluginFactoryProvider] by providing it with
- the type of device ([AndroidPcscPluginFactory.DeviceType]),
- the Android context.

```kotlin
// initialization
pluginFactory = AndroidPcscPluginFactoryProvider.getFactory(AndroidPcscPluginFactory.DeviceType.USB, activity)
// registration
androidPcscPlugin = smartCardService.registerPlugin(pluginFactory) as ObservablePlugin
```

## Register and observe
Submit the obtained factory to the smart card service of Keyple and get back the plugin.
<br>
Become an observer of the plugin to retrieve the readers from the events generated during their connection.
```kotlin
// set up observation
androidPcscPlugin.setPluginObservationExceptionHandler(this)
androidPcscPlugin.addObserver(this)
```

## Reader discovery

Start the device scanning with [AndroidPcscPlugin.scanDevices].
<br>
Handle the result received by the class implementing *DeviceScannerSpi*.
```kotlin
// discover readers
androidPcscPlugin
    .getExtension(AndroidPcscPlugin::class.java)
    .scanDevices(2, true, this)

override fun onDeviceDiscovered(deviceInfoList: MutableCollection<DeviceInfo>) {
    // connect to first discovered device
    if (deviceInfoList.isNotEmpty()) {
      val device = deviceInfoList.first()
      androidPcscPlugin
          .getExtension(AndroidPcscPlugin::class.java)
          .connectToDevice(device.identifier)
    } else {
        // handle empty list
    }
}
```

Become an observer of the needed reader.

```kotlin
override fun onPluginEvent(pluginEvent: PluginEvent?) {
    if (pluginEvent != null) {
        if (pluginEvent.type == PluginEvent.Type.READER_CONNECTED) {
            // connect to the reader identified by its name
            for (readerName in pluginEvent.readerNames) {
                if (readerName.toUpperCase().contains("CONTACTLESS")) {
                    cardReader = androidPcscPlugin.getReader(readerName) as ObservableReader
                    if (cardReader != null) {
                        cardReader!!.getExtension(AndroidPcscReader::class.java)
                            .setContactless(true)
                        cardReader!!.setReaderObservationExceptionHandler { pluginName, readerName, e ->
                            // handle plugin observation exception here
                        }
                        cardReader!!.addObserver(this)
                        cardReader!!.startCardDetection(ObservableCardReader.DetectionMode.REPEATING)
                        // prepare scheduled selection here        
                    }
                }
            }
            if (pluginEvent.type == PluginEvent.Type.READER_DISCONNECTED) {
                for (readerName in pluginEvent.readerNames) {
                    // notify reader disconnection
                }
            }
        }
    }
}
```

## Example application

An example of implementation is available in the **example-app** folder.

This example implements the plugin with the choice of USB or BLE mode.

The card scenario corresponds to a Calypso transaction with or without SAM.

It has been tested with SpringCard readers [Puck One](https://www.springcard.com/fr/products/puck-one) et [Puck Blue](https://www.springcard.com/fr/products/puck-blue)

## About the source code

The code is built with **Gradle** and is compliant with **Java 1.8** in order to address a wide range of applications.