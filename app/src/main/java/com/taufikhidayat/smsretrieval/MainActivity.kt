package com.taufikhidayat.smsretrieval

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.taufikhidayat.smsretrieval.databinding.ActivityMainBinding
import com.taufikhidayat.smsretrieval.helper.AppSignatureHelper
import com.taufikhidayat.smsretrieval.receiver.SmsRetrieverBroadcastReceiver
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private var otpAdapter: OtpAdapter? = null
    private var countDownTimer: CountDownTimer? = null

    private val timeInMillis: Long = 1 * 60 * 1000 // 5 minutes
    private var millisLeft: Long = 0L
    private val countDownInterval = 1000L

    private val smsRetrieverBroadcastReceiver = SmsRetrieverBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        handleUi()
        setAdapter()
        handleClickEvent()
        collectOtpResult()

        // This code below only runs once. Please make sure that it runs on development/debug mode
        val appSignatureHelper = AppSignatureHelper(this)
        println("App hash-code: ${appSignatureHelper.appSignatures}")
    }

    private fun handleUi() {
        binding.apply {
            tvTimer.text = timeInMillis.convertToTimeFormat()

            btnRequestOtp.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.black))
            binding.tvResendOTP.isEnabled = false
            binding.tvResendOTP.setTextColor(Color.GRAY)
        }
    }

    private fun setAdapter() {
        otpAdapter = OtpAdapter(6, binding.rvOtp)
        binding.rvOtp.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = otpAdapter
        }
    }

    private fun handleClickEvent() {
        binding.apply {
            btnRequestOtp.setOnClickListener {
                otpAdapter?.clearOtp() // Clear OTP input fields
                startSmsRetriever()
                startCountDownTimer()
            }

            tvResendOTP.setOnClickListener {
                if (it.isEnabled) {
                    otpAdapter?.clearOtp() // Clear OTP input fields
                    startSmsRetriever()
                    startCountDownTimer()
                }
            }
        }
    }

    private fun startSmsRetriever() {
        val client = SmsRetriever.getClient(this)
        val task = client.startSmsRetriever()

        task.addOnSuccessListener {
            otpAdapter?.getOtp()
        }

        task.addOnFailureListener {
            Toast.makeText(this, "Failed to start SMS retriever", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCountDownTimer() {
        countDownTimer?.cancel() // Cancel any existing timer
        binding.apply {
            countDownTimer = object : CountDownTimer(timeInMillis, countDownInterval) { // 5 minutes
                override fun onTick(millisUntilFinished: Long) {
                    millisLeft = millisUntilFinished
                    tvTimer.text = millisUntilFinished.convertToTimeFormat()

                    // Disable the resend OTP button while timer is running
                    disableResendOtp(true)
                }

                override fun onFinish() {
                    disableResendOtp(false)
                }
            }.start()
        }
    }

    private fun collectOtpResult() {
        lifecycleScope.launch {
            smsRetrieverBroadcastReceiver.otpResult.collect { result ->
                when (result) {
                    is SmsRetrieverBroadcastReceiver.OTPResult.OTPReceived -> otpAdapter?.setOtp(result.otp)
                    is SmsRetrieverBroadcastReceiver.OTPResult.OTPNotReceived -> Log.e(TAG, "collectOtpResult: ${result.error}")
                }
            }
        }
    }

    private fun disableResendOtp(isDisable: Boolean) {
        binding.apply {
            if (isDisable) {
                tvResendOTP.isEnabled = false
                tvResendOTP.setTextColor(Color.GRAY)
                btnRequestOtp.isEnabled = false
                btnRequestOtp.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.grey))
            } else {
                tvTimer.text = "00:00"
                tvResendOTP.isEnabled = true
                tvResendOTP.setTextColor(Color.BLACK)
                btnRequestOtp.isEnabled = true
                btnRequestOtp.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.black))
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsRetrieverBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsRetrieverBroadcastReceiver, intentFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(smsRetrieverBroadcastReceiver)
        countDownTimer?.cancel()
    }

    override fun onResume() {
        super.onResume()

        countDownTimer?.cancel() // Cancel any existing timer
        binding.apply {
            if (countDownTimer == null) {
                countDownTimer = object : CountDownTimer(millisLeft, 1000) { // 5 minutes
                    override fun onTick(millisUntilFinished: Long) {
                        tvTimer.text = millisUntilFinished.convertToTimeFormat()

                        // Disable the resend OTP button while timer is running
                        disableResendOtp(true)
                    }

                    override fun onFinish() {
                        disableResendOtp(false)
                    }
                }.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        countDownTimer = null
        unregisterReceiver(smsRetrieverBroadcastReceiver)
    }

    private fun Long.convertToTimeFormat(): String {
        val minutes = (this / 1000).toInt() / 60
        val seconds = (this / 1000).toInt() % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    companion object {
        private const val TAG = "MainActivityTAG"
    }
}