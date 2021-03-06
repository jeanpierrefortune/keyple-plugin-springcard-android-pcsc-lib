/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.os.ConditionVariable
import com.springcard.pcsclike.SCardReader
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.ReaderIOException
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionBlockingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.DontWaitForCardRemovalDuringProcessingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.WaitForCardRemovalBlockingSpi
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

/**
 * Andorid Pcsc Reader implementation
 * @since 1.0.0
 */
internal class AndroidPcscReaderAdapter(val sCardReader: SCardReader) :
    AndroidPcscReader,
    ObservableReaderSpi,
    WaitForCardInsertionBlockingSpi,
    DontWaitForCardRemovalDuringProcessingSpi,
    WaitForCardRemovalBlockingSpi {

  private val name: String = sCardReader.name
  private var isContactless: Boolean = false
  private var isCardPresent: Boolean = false

  private val WAIT_RESPONSE_TIMEOUT: Long = 5000
  private val WAIT_CARD_CONNECT_TIMEOUT: Long = 5000
  private val waitCardStatusChange = ConditionVariable()
  private val waitCardConnect = ConditionVariable()
  private val waitCardResponse = ConditionVariable()
  private lateinit var cardResponse: ByteArray

  override fun getName(): String {
    return name
  }

  override fun openPhysicalChannel() {
    Timber.v("Open physical channel")
    try {
      sCardReader.cardConnect()
      waitCardConnect.block(WAIT_CARD_CONNECT_TIMEOUT)
      waitCardConnect.close()
    } catch (e: Exception) {
      throw ReaderIOException(getName() + ": Error while opening Physical Channel", e)
    }
  }

  override fun closePhysicalChannel() {
    Timber.v("Close physical channel")
    // may be invoked at any time, run in a separate thread
    synchronized(sCardReader) { Runnable { sCardReader.channel.disconnect() } }
  }

  override fun isPhysicalChannelOpen(): Boolean {
    val isCardConnected = sCardReader.cardConnected
    Timber.v("Physical channel is open: %b", isCardConnected)
    return isCardConnected
  }

  override fun checkCardPresence(): Boolean {
    isCardPresent = sCardReader.cardPresent
    Timber.v("Card present: %b", isCardPresent)
    return isCardPresent
  }

  override fun getPowerOnData(): String {
    return ByteArrayUtil.toHex(sCardReader.channel.atr)
  }

  override fun transmitApdu(apduIn: ByteArray?): ByteArray {
    if (apduIn != null) {
      sCardReader.channel.transmit(apduIn)
      waitCardResponse.block(WAIT_RESPONSE_TIMEOUT)
      waitCardResponse.close()
      synchronized(cardResponse) {
        if (cardResponse.isEmpty()) {
          throw CardIOException("[${this.name}]: not response received from the card.")
        }
      }
      return cardResponse
    } else {
      throw ReaderIOException("[${this.name}]: null channel.")
    }
  }

  override fun isContactless(): Boolean {
    return isContactless
  }

  override fun onUnregister() {
    Timber.i("Unregister reader '%s'", name)
  }

  override fun onStartDetection() {
    Timber.i("Starting card detection on reader '%s'", name)
  }

  override fun onStopDetection() {
    Timber.i("Stopping card detection on reader '%s'", name)
  }

  override fun waitForCardRemoval() {
    do {
      waitCardStatusChange.block()
      waitCardStatusChange.close()
    } while (isCardPresent)
  }

  override fun stopWaitForCardRemoval() {
    waitCardStatusChange.close()
  }

  override fun waitForCardInsertion() {
    do {
      waitCardStatusChange.block()
      waitCardStatusChange.close()
    } while (!isCardPresent)
  }

  override fun stopWaitForCardInsertion() {
    waitCardStatusChange.close()
  }

  override fun setContactless(contactless: Boolean): AndroidPcscReader {
    isContactless = contactless
    return this
  }

  fun onCardPresenceChange(isCardPresent: Boolean) {
    Timber.e("Reader '%s', card presence changed: %b", name, isCardPresent)
    this.isCardPresent = isCardPresent
    waitCardStatusChange.open()
    if (!isCardPresent) {
      // to shorten a possible transmission of apdu
      Timber.e("Shorten transmit APDU")
      synchronized(this) { this.cardResponse = byteArrayOf() }
      // waitCardResponse.open()
    }
  }

  fun onCardConnected() {
    waitCardConnect.open()
  }

  fun onCardResponseReceived(cardResponse: ByteArray) {
    Timber.d("Reader '%s', %d bytes received from the card", name, cardResponse.size)
    synchronized(this) { this.cardResponse = cardResponse }
    waitCardResponse.open()
  }

  fun onReaderOrCardError() {
    synchronized(this) { this.cardResponse = byteArrayOf() }
    waitCardResponse.open()
  }
}
