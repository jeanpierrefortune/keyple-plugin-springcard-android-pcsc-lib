/* **************************************************************************************
 * Copyright (c) 2021 SpringCard - https://www.springcard.com/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package com.springcard.keyple.plugin.pcsclike.example.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import com.springcard.keyple.plugin.pcsclike.example.R
import com.springcard.keyple.plugin.pcsclike.example.model.ChoiceEventModel
import com.springcard.keyple.plugin.pcsclike.example.model.EventModel
import com.springcard.keyple.plugin.pcsclike.example.util.getColorResource
import kotlinx.android.synthetic.main.card_action_event.view.cardActionTextView
import kotlinx.android.synthetic.main.card_choice_event.view.choiceRadioGroup

class EventAdapter(private val events: ArrayList<EventModel>) :
    RecyclerView.Adapter<EventAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    return when (viewType) {
      EventModel.TYPE_ACTION ->
          ViewHolder(
              LayoutInflater.from(parent.context)
                  .inflate(R.layout.card_action_event, parent, false))
      EventModel.TYPE_RESULT ->
          ViewHolder(
              LayoutInflater.from(parent.context)
                  .inflate(R.layout.card_result_event, parent, false))
      EventModel.TYPE_MULTICHOICE ->
          ChoiceViewHolder(
              LayoutInflater.from(parent.context)
                  .inflate(R.layout.card_choice_event, parent, false))
      else ->
          ViewHolder(
              LayoutInflater.from(parent.context)
                  .inflate(R.layout.card_header_event, parent, false))
    }
  }

  override fun getItemCount(): Int {
    return events.size
  }

  override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
    viewHolder.bind(events[position])
  }

  override fun getItemViewType(position: Int): Int {
    return events[position].type
  }

  open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    open fun bind(event: EventModel) {
      with(itemView) { cardActionTextView.text = event.text }
    }
  }

  class ChoiceViewHolder(itemView: View) : ViewHolder(itemView) {
    override fun bind(event: EventModel) {
      super.bind(event)
      with(itemView) {
        choiceRadioGroup.removeAllViews()
        (event as ChoiceEventModel).choices.forEachIndexed { index, choice ->
          val button = RadioButton(this.context)
          button.text = choice
          button.id = index
          button.setOnClickListener { event.callback(choice) }
          button.setTextColor(context.getColorResource(R.color.textColorPrimary))
          choiceRadioGroup.addView(button)
        }
      }
    }
  }
}
