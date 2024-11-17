package com.example.publisherapplication

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var studentID : String = ""

    private var client: Mqtt5BlockingClient? = null
    private var isPublishing: Boolean = false
    private lateinit var tvStatusMessage: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback: LocationCallback

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStatusMessage = findViewById(R.id.tvStatusMessage)
        tvStatusMessage.text = getString(R.string.not_publishing_location)

        /* SET UP BROKER */
        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816028524.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()
    }

    /* CALLED WHEN 'START PUBLISHING' BUTTON PRESSED - starts receiving location information from hardware and connects to broker*/
    fun startLocationPublish(view: View?) {
        if (isPublishing) {
            Toast.makeText(this, "Location already being published", Toast.LENGTH_SHORT).show()
            return
        }
        /* VERIFY STUDENT ID FIRST */
        if (isValidStudentID()) {
            try {
                /* SET UP LOCATION SERVICE */
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                locationRequest = LocationRequest.create().apply {
                    interval = 10000
                    fastestInterval = 5000
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                }
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            var speedInKmh = location.speed * 3.6
                            speedInKmh = String.format("%.3f", speedInKmh).toDouble()
                            val locationData = """
                            {
                                "studentID": $studentID,
                                "latitude": ${location.latitude},
                                "longitude": ${location.longitude},
                                "speed": ${speedInKmh},
                                "timestamp": ${location.time}
                            } """.trimIndent()

                            Log.d("LocationData", locationData)

                            /* PUBLISH LOCATION INFORMATION TO BROKER */
                            try {
                                client?.publishWith()?.topic("the/location")?.payload(locationData.toByteArray())?.send()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "An error occurred when sending a message to the broker", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                try {
                    client?.connect()
                    Toast.makeText(this,"Successfully connected to broker", Toast.LENGTH_SHORT).show()
                    tvStatusMessage.text = getString(R.string.publishing_location)
                } catch (e:Exception){
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    Toast.makeText(this,"An error occurred when connecting to broker", Toast.LENGTH_SHORT).show()
                }

                isPublishing = true
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission issue with location updates", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "Please enter a valid Student ID in the range 816000000-816999999", Toast.LENGTH_SHORT).show()
        }
    }

    /* CALLED WHEN 'STOP PUBLISHING' BUTTON PRESSED - disconnects from broker and stops receiving location information from hardware */
    fun stopLocationPublish(view: View?) {
        if (!isPublishing){
            Toast.makeText(this, "Location has already stopped being published", Toast.LENGTH_SHORT).show()
            return;
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isPublishing = false

        try {
            client?.disconnect()
            Toast.makeText(this,"Successfully disconnected from broker", Toast.LENGTH_SHORT).show()
        } catch (e:Exception){
            Toast.makeText(this,"An error occurred when disconnecting from broker", Toast.LENGTH_SHORT).show()
        }
        tvStatusMessage.text = getString(R.string.not_publishing_location)
    }

    /* HELPER METHOD TO VALIDATE STUDENT ID */
    private fun isValidStudentID(): Boolean {
        studentID = findViewById<EditText>(R.id.etEnterStudentID).text.toString()

        if (studentID.length == 9) {
            val studentIdNumber = studentID.toLongOrNull()
            return studentIdNumber != null && studentIdNumber in 816000000..816999999
        }
        return false
    }
}