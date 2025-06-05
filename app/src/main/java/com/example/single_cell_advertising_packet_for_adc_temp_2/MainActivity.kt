package com.example.single_cell_advertising_packet_for_adc_temp_2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.single_cell_advertising_packet_for_adc_temp_2.ui.theme.Single_Cell_Advertising_Packet_for_ADC_TEMP_2Theme

class MainActivity : ComponentActivity() {

    private val advertisingPackets = mutableStateListOf<String>()
    private var adcValue = mutableStateOf("--")
    private var tempValue = mutableStateOf("--")
    private val maxEntries = 20
    private val targetAddress = "58:35:0F:DC:8D:C7"
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                startScan()
            }
        }

    private fun arePermissionsGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun convertAdcToVoltage(bytes: List<Byte>): String {
        // First two bytes (e.g., 0x06D0) represent the ADC value
        val adcMsb = bytes[0].toInt() and 0xFF
        val adcLsb = bytes[1].toInt() and 0xFF
        
        // Combine bytes to get 16-bit value (big-endian format)
        val adcValue = (adcMsb shl 8) or adcLsb
        
        // Convert to voltage (in volts)
        val voltageInVolts = adcValue / 1000.0 * (178.0 + 150.0) / (150.0)
        
        return String.format("%.2fV", voltageInVolts)
    }
    
    private fun convertToTemperature(bytes: List<Byte>): String {
        // Next two bytes (e.g., 0x6750) represent the temperature value
        val tempMsb = bytes[2].toInt() and 0xFF
        val tempLsb = bytes[3].toInt() and 0xFF
        
        // Combine bytes to get 16-bit value (big-endian format)
        val tempValue = (tempMsb shl 8) or tempLsb
        
        // Apply temperature conversion formula
        val tempCelsius = -45.0 + (175.0 * tempValue / 65535.0)
        
        return String.format("%.2f°C", tempCelsius)
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address == targetAddress) {
                val bytes = result.scanRecord?.bytes ?: return

                val firstSixBytes = bytes.take(6)

                val hex = firstSixBytes.joinToString(" ") { String.format("%02X", it) }
                
                // Convert and update the ADC and temperature values
                adcValue.value = convertAdcToVoltage(firstSixBytes)
                tempValue.value = convertToTemperature(firstSixBytes)

                advertisingPackets.add("$hex")

                if (advertisingPackets.size > maxEntries) {
                    advertisingPackets.removeAt(0)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (arePermissionsGranted()) {
            startScan()
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }

        setContent {
            Single_Cell_Advertising_Packet_for_ADC_TEMP_2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Display ADC value
                        Text(
                            text = "ADC Value:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = adcValue.value,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Display Temperature value
                        Text(
                            text = "Temperature:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = tempValue.value,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Display raw advertising data
                        Text(
                            text = "Raw Data:",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                        AdvertisingList(
                            packets = advertisingPackets,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    private fun startScan() {
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    private fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }
}

@Composable
fun AdvertisingList(packets: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(packets) { packet ->
            Text(
                text = packet,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}