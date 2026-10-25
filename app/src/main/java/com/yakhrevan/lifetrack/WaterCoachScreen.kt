package com.yakhrevan.lifetrack

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WaterCoachScreen(logs:List<LogEntry>,today:String,onWaterAdded:(Int)->Unit,onEditProfile:()->Unit) {
    WaterScreen(
        logs = logs,
        today = today,
        onWaterLogged = { amount, _, _ -> onWaterAdded(amount) }
    )
}

@Composable
private fun LegacyWaterCoachScreen(logs:List<LogEntry>,today:String,onWaterAdded:(Int)->Unit,onEditProfile:()->Unit) {
    val context= LocalContext.current
    val prefs= remember { context.getSharedPreferences("lifetrack_profile",Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name","") ?: "") }
    var height by remember { mutableStateOf(prefs.getString("height","") ?: "") }
    var weight by remember { mutableStateOf(prefs.getString("weight","") ?: "") }
    var bottle by remember { mutableStateOf(prefs.getString("bottle","750") ?: "750") }
    var target by remember { mutableIntStateOf(prefs.getInt("water_target",2500)) }
    var interval by remember { mutableIntStateOf(prefs.getInt("reminder_interval",60)) }
    var spoken by remember { mutableStateOf("") }
    var customWaterAmount by remember { mutableStateOf("") }
    var customWaterError by remember { mutableStateOf<String?>(null) }
    // Totals come from the saved log entries (same source as Home/Insights).
    // The legacy "water_total_<date>" prefs are no longer written; they duplicated log entries.
    val todayWater = remember(logs, today) { WaterTotals.dayTotal(logs, today) }
    val waterHistory = remember(logs, today) { WaterTotals.history(logs, today, 7) }
    fun addWater(amount: Int) {
        if (amount <= 0) return
        onWaterAdded(amount)
    }
    var weatherLabel by remember { mutableStateOf(prefs.getString("weather_label","Weather not checked") ?: "Weather not checked") }
    var weatherAdjustment by remember { mutableIntStateOf(prefs.getInt("weather_adjustment",0)) }
    var weatherBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var remindersEnabled by remember { mutableStateOf(prefs.getBoolean("water_reminders_enabled",false)) }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindersEnabled = granted
        prefs.edit().putBoolean("water_reminders_enabled", granted).putInt("reminder_interval", interval).apply()
        scheduleWaterReminders(context, interval, granted)
    }
    val bmi= remember(height,weight) {
        val h=(height.toFloatOrNull() ?: 0f)/100f
        val w=weight.toFloatOrNull() ?: 0f
        if(h>0f && w>0f) w/(h*h) else 0f
    }
    suspend fun refreshWeather() {
        weatherBusy=true
        try {
            val lm=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val granted=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            val last=if(granted) try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_:SecurityException) { null } else null
            val lat=last?.latitude; val lon=last?.longitude
            if(lat==null || lon==null) { weatherLabel="Weather unavailable · saved target unchanged"; weatherAdjustment=0 }
            else {
                val data=withContext(Dispatchers.IO) {
                    val connection=(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature&timezone=auto").openConnection() as HttpURLConnection).apply { connectTimeout=5000; readTimeout=5000 }
                    try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current") } finally { connection.disconnect() }
                }
                val temp=data.optDouble("temperature_2m",25.0); val humidity=data.optInt("relative_humidity_2m",50); val feels=data.optDouble("apparent_temperature",temp)
                weatherAdjustment=when { feels>=35.0 -> 400; feels>=30.0 -> 250; temp>=28.0 && humidity>=75 -> 200; else -> 0 }
                weatherLabel="${temp.toInt()}°C · ${humidity}% humidity"
                prefs.edit().putString("weather_label",weatherLabel).putInt("weather_adjustment",weatherAdjustment).putLong("weather_checked_at",System.currentTimeMillis()).apply()
            }
        } catch (_:Exception) { weatherLabel="Weather unavailable · saved target unchanged"; weatherAdjustment=0 }
        finally { weatherBusy=false }
    }
    val locationLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants -> if(grants.values.any { it }) scope.launch { refreshWeather() } }
    LaunchedEffect(Unit) {
        val hasPermission=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(hasPermission) refreshWeather()
    }
    val voiceLauncher= rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            val phrase=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            spoken=phrase
            val normalized=phrase.lowercase(Locale.getDefault())
            val bottleMl=bottle.toIntOrNull() ?: 750
            val amount=when {
                Regex("half\\s*(a\\s*)?bottle|half bottle").containsMatchIn(normalized) -> bottleMl/2
                Regex("one\\s*bottle|full bottle|whole bottle").containsMatchIn(normalized) -> bottleMl
                Regex("quarter\\s*(a\\s*)?bottle").containsMatchIn(normalized) -> bottleMl/4
                Regex("(\\d+)\\s*(ml|millilitre|milliliter)").containsMatchIn(normalized) -> Regex("(\\d+)\\s*(ml|millilitre|milliliter)").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                Regex("(\\d+)\\s*(litre|liter|l)\\b").containsMatchIn(normalized) -> (Regex("(\\d+)\\s*(litre|liter|l)\\b").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 0)*1000
                else -> 0
            }
            if(amount>0) addWater(amount)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=32.dp,top=8.dp)) {
        item {
            Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.SurfaceRaised),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if(name.isBlank()) "WATER TRACKING" else "HI, ${name.uppercase()}",color=LifeTrackColors.Primary,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                            Text("Stay hydrated, your way.",color=LifeTrackColors.TextPrimary,fontSize=22.sp,fontWeight=FontWeight.Bold)
                        }
                        Icon(Icons.Default.WaterDrop,null,tint=LifeTrackColors.Blue,modifier=Modifier.size(34.dp))
                    }
                    Text("${"%.1f".format(Locale.US,todayWater/1000f)} L logged today · Target ${target/1000f} L",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    LinearProgressIndicator(progress={(todayWater.toFloat()/target.toFloat()).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(7.dp),color=LifeTrackColors.Blue,trackColor=Color.White.copy(alpha=.12f))
                }
            }
        }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.Surface),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("HYDRATION HISTORY",color=LifeTrackColors.TextPrimary,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text("Daily totals from your water log",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    waterHistory.reversed().forEach { (date, amount) ->
                        val progress=(amount.toFloat()/target.toFloat()).coerceIn(0f,1f)
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(date,color=LifeTrackColors.TextSecondary,fontSize=12.sp,modifier=Modifier.weight(1f))
                                Text(String.format(Locale.US,"%.2f L",amount/1000f),color=LifeTrackColors.TextPrimary,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(4.dp),color=if(amount>=target) LifeTrackColors.Primary else LifeTrackColors.Blue,trackColor=LifeTrackColors.Background)
                        }
                    }
                }
            }
        }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.Surface),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Quick add",color=LifeTrackColors.TextPrimary,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("Bottle capacity: ${bottle.ifBlank{"750"}} ml",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(150,250,500,(bottle.toIntOrNull()?:750)).distinct().forEach { ml ->
                            Button(onClick={addWater(ml)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp),colors=ButtonDefaults.buttonColors(containerColor=LifeTrackColors.SurfaceRaised,contentColor=LifeTrackColors.Blue)) { Text("${ml}ml",fontSize=11.sp) }
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value=customWaterAmount,onValueChange={value->customWaterAmount=value.filter(Char::isDigit);customWaterError=null},label={Text("Custom amount (ml)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f))
                        Button(onClick={
                            val amount=customWaterAmount.toIntOrNull()
                            if(amount==null || amount !in 1..5000) customWaterError="Enter an amount from 1 to 5000 ml."
                            else { addWater(amount); customWaterAmount=""; customWaterError=null }
                        }) { Text("Add") }
                    }
                    if(customWaterError!=null) Text(customWaterError!!,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                    Button(onClick={
                        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_PROMPT,"Say: I drank 500 ml, half bottle, or one bottle")
                        }
                        try { voiceLauncher.launch(intent) } catch (_:Exception) { spoken="Voice recognition is not available on this device." }
                    },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=LifeTrackColors.Primary,contentColor=LifeTrackColors.Background)) {
                        Icon(Icons.Default.Mic,null); Spacer(Modifier.width(8.dp)); Text("Tell LifeTrack by voice")
                    }
                    if(spoken.isNotBlank()) Text("Heard: $spoken",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                }
            }
        }
        item {
            Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.Surface),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Personal hydration settings",color=LifeTrackColors.TextPrimary,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    OutlinedTextField(value=bottle,onValueChange={bottle=it.filter(Char::isDigit);prefs.edit().putString("bottle",bottle).apply()},label={Text("Bottle capacity (ml)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
                    Text("Local weather: $weatherLabel",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    Text("Weather suggestion: " + if(weatherAdjustment>0) "$weatherAdjustment ml" else "none",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    Text("Privacy: weather lookup uses your device last-known location only after you grant location permission. Coordinates are sent to Open-Meteo to retrieve weather. You can deny permission and continue without weather data.",color=LifeTrackColors.TextSecondary,fontSize=11.sp)
                    Text("Hydration target is an approximate wellness estimate, not medical advice. Adjust it to your needs and follow advice from your healthcare professional.",color=LifeTrackColors.TextSecondary,fontSize=11.sp)
                    OutlinedButton(onClick={
                        val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                        val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
                        if(fine||coarse) scope.launch { refreshWeather() }
                        else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                    },modifier=Modifier.fillMaxWidth(),enabled=!weatherBusy) { Text(if(weatherBusy) "Checking weather…" else "Enable / refresh local weather") }
                    Text("Saved daily target: ${(target/1000f)} L. Change it in Goals & Targets; weather suggestions do not overwrite your goal.",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    Text("Automatic reminder spacing: about $interval minutes across a typical 16-hour waking day.",color=LifeTrackColors.TextSecondary,fontSize=12.sp)
                    Text("BMI: ${if(bmi>0) "%.1f".format(Locale.US,bmi) else "Add height and weight in your profile"}",color=LifeTrackColors.Primary,fontWeight=FontWeight.SemiBold)
                    Text("BMI is a screening measure, not a diagnosis. Water needs vary with heat, activity and health; adjust your target rather than treating one number as medical advice.",color=LifeTrackColors.TextSecondary,fontSize=11.sp)
                    Button(onClick={
                        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else {
                            remindersEnabled = !remindersEnabled
                            prefs.edit().putBoolean("water_reminders_enabled", remindersEnabled).putInt("reminder_interval", interval).apply()
                            scheduleWaterReminders(context, interval, remindersEnabled)
                        }
                    },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=if(remindersEnabled)LifeTrackColors.Primary else LifeTrackColors.SurfaceRaised,contentColor=if(remindersEnabled)LifeTrackColors.Background else LifeTrackColors.TextPrimary)) { Icon(Icons.Default.Notifications,null); Spacer(Modifier.width(8.dp)); Text(if(remindersEnabled)"Water reminders ON · every $interval min" else "Enable water reminders") }
                    OutlinedButton(onClick=onEditProfile,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit,null); Spacer(Modifier.width(6.dp)); Text("Edit personal profile in Settings") }
                }
            }
        }
        item { Text("Voice commands supported: “I drank 500 ml”, “half bottle”, “one bottle”, or “I drank 1 litre”. Review the amount shown before relying on it.",color=LifeTrackColors.TextSecondary,fontSize=12.sp) }
    }
}