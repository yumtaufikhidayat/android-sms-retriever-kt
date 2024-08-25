package com.taufikhidayat.smsretrieval

import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.taufikhidayat.smsretrieval.databinding.ItemOtpEditBinding

class OtpAdapter(
    private val otpLength: Int,
    private val recyclerView: RecyclerView
) : RecyclerView.Adapter<OtpAdapter.OtpViewHolder>() {

    private val otpArray = CharArray(otpLength)

    inner class OtpViewHolder(val binding: ItemOtpEditBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OtpViewHolder =
        OtpViewHolder(
            ItemOtpEditBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: OtpViewHolder, position: Int) {
        holder.binding.otpEditText.apply {
            setText(otpArray[position].toString())

            // Handle paste event
            setOnPasteListener { pastedText ->
                val otpDigits = pastedText.filter { it.isDigit() }
                if (otpDigits.length == otpLength) {
                    otpDigits.forEachIndexed { index, char ->
                        otpArray[index] = char
                        val holders = recyclerView.findViewHolderForAdapterPosition(index) as? OtpViewHolder
                        holders?.binding?.otpEditText?.setText(char.toString())
                    }
                }
            }

            addTextChangedListener(onTextChanged = { chars, _, before, _ ->
                val pos = holder.adapterPosition
                when {
                    chars?.length == 1 -> {
                        otpArray[pos] = chars[0]
                        if (pos < otpLength - 1) {
                            val nextHolder =
                                recyclerView.findViewHolderForAdapterPosition(pos + 1) as? OtpViewHolder
                            nextHolder?.binding?.otpEditText?.requestFocus()
                        }
                    }

                    // Move to the previous EditText when the character is deleted
                    chars?.length == 0 && before == 1 -> {
                        otpArray[pos] = '\u0000'
                        if (pos > 0) {
                            val prevHolder = recyclerView.findViewHolderForAdapterPosition(pos - 1) as? OtpViewHolder
                            prevHolder?.binding?.otpEditText?.apply {
                                requestFocus()
                                setSelection(text?.length ?: 0)
                            }
                        }
                    }
                }
            })
        }
    }

    override fun getItemCount(): Int = otpLength

    fun getOtp(): String = String(otpArray)

    fun setOtp(otp: String) {
        otp.toCharArray().forEachIndexed { index, char ->
            otpArray[index] = char
            notifyItemChanged(index)
        }
    }

    fun clearOtp() {
        otpArray.fill('\u0000')
        notifyDataSetChanged()
    }

    // Extension function to detect paste event
    private fun EditText.setOnPasteListener(onPaste: (String) -> Unit) {
        this.setOnCreateContextMenuListener { _, _, _ ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                clipboard.primaryClip?.getItemAt(0)?.text?.let { pastedText ->
                    // Only consider pasted text if it contains only digits
                    if (pastedText.all { it.isDigit() }) {
                        onPaste(pastedText.toString())
                    }
                }
            }
        }
    }
}