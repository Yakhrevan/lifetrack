package com.yakhrevan.lifetrack

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors

private data class ParsedVoiceEntry(val category:String,val title:String,val amount:String,val note:String)

private fun parseVoiceEntry(text:String):ParsedVoiceEntry? {
    val s=text.lowercase(Locale.ROOT)
    val numberPatterns = listOf(
        Regex("(?:rs\\.?|inr|₹)\\s*([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:rupees?|rs)"),
        Regex("([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:ml|millilit(?:er|re)s?|lit(?:er|re)s?)"),
        Regex("\\b([0-9]+(?:\\.[0-9]{1,2})?)\\b")
    )
    val number = numberPatterns.firstNotNullOfOrNull { it.find(s)?.groupValues?.getOrNull(1) }
    val isIncome=listOf("income","salary","received","earned","வந்தது","சம்பளம்","வருமானம்","கிடைத்தது","പണം കിട്ടി","आय","मिला").any{s.contains(it)}
    val isExpense=listOf("expense","spent","paid","bought","shopping","செலவு","வாங்கினேன்","கொடுத்தேன்","खर्च","खरीदा").any{s.contains(it)}
    val isWater=listOf("water","ml","litre","liter","தண்ணீர்","குடித்தேன்","पानी","पिया").any{s.contains(it)}
    val isStudy=listOf("study","studied","learning","படித்தேன்","படிப்பு","पढ़ाई","अभ्यास").any{s.contains(it)}
    val isMeal=listOf("meal","breakfast","lunch","dinner","snack","சாப்பிட்டேன்","உணவு","காலை உணவு","மதிய உணவு","இரவு உணவு","भोजन","नाश्ता").any{s.contains(it)}
    val isCare=listOf("habit","exercise","workout","meditate","read","stretch","நடைப்பயிற்சி","உடற்பயிற்சி","தியானம்","पढ़ा","व्यायाम").any{s.contains(it)}
    val isFaceRoutine=listOf("skincare","skin care","face routine","brush teeth","முக பராமரிப்பு","பல் துலக்கினேன்").any{s.contains(it)}
    if(number==null) return null
    val amount=number.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount <= 0.0 || amount > 1_000_000.0) return null
    return when {
        isWater -> {
            val ml=when {
                Regex("\\b[0-9]+\\s*(litre|liter|l)\\b").containsMatchIn(s) -> (amount*1000).toInt()
                Regex("\\b[0-9]+\\s*ml\\b").containsMatchIn(s) -> amount.toInt()
                else -> amount.toInt()
            }
            ParsedVoiceEntry("Water","Voice water log",ml.toString(),"Recognised speech: $text")
        }
        isIncome -> ParsedVoiceEntry("Income","Voice income",amount.toString(),"Recognised speech: $text")
        isExpense -> ParsedVoiceEntry("Expense","Voice expense",amount.toString(),"Recognised speech: $text")
        isStudy -> ParsedVoiceEntry("Study","Study session",amount.toInt().toString(),"Recognised speech: $text")
        isMeal -> ParsedVoiceEntry("Meal","Meal log",amount.toInt().toString(),"Recognised speech: $text")
        isFaceRoutine -> ParsedVoiceEntry("Face routine","Personal-care routine",amount.toInt().toString(),"Recognised speech: $text")
        isCare -> ParsedVoiceEntry("Care","Habit log",amount.toInt().toString(),"Recognised speech: $text")
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(onSave:(String,String,String,String)->Unit) {
    var language by remember { mutableStateOf("ta-IN") }
    var recognized by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedVoiceEntry?>(null) }
    var error by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("Water") }
    var editTitle by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    val options=listOf("Tamil" to "ta-IN","English (India)" to "en-IN","Hindi" to "hi-IN","Malayalam" to "ml-IN","Telugu" to "te-IN","Kannada" to "kn-IN")
    val launcher= rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            recognized=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            parsed=parseVoiceEntry(recognized)
            parsed?.let { entry -> editCategory=entry.category; editTitle=entry.title; editAmount=entry.amount; editNote=entry.note }
            error=if(parsed==null) "I heard the words, but couldn't safely identify the category and amount. Edit the wording or use manual entry." else ""
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=28.dp)) {
        item { Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.Surface),shape=RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) { Text("VOICE LOGGING",color=LifeTrackColors.Primary,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp); Text("Say it. Review it. Save it.",color=LifeTrackColors.TextPrimary,fontSize=22.sp,fontWeight=FontWeight.Bold); Text("Speak a water amount, expense, income or study duration. Always review the interpretation before saving.",color=LifeTrackColors.TextSecondary,fontSize=12.sp) } } }
        item { Text("Speech language",fontWeight=FontWeight.Bold,fontSize=17.sp) }
        item { Column(verticalArrangement=Arrangement.spacedBy(7.dp)) { options.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { row.forEach { (label,code) -> OutlinedButton(onClick={language=code},modifier=Modifier.weight(1f),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(language==code)LifeTrackColors.SurfaceRaised else Color.Transparent)) { Text(label,fontSize=11.sp) } } } } } }
        item { Button(onClick={
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,language)
                putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak the activity and amount")
            }
            try { launcher.launch(intent); error="" } catch (_:Exception) { error="Speech recognition is unavailable. Try another keyboard/voice input app or type the entry manually." }
        },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=LifeTrackColors.Primary,contentColor=LifeTrackColors.TextPrimary)) { Icon(Icons.Default.Mic,null); Spacer(Modifier.width(8.dp)); Text("Tap to speak") } }
        if(recognized.isNotBlank()) item { Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.Surface),shape=RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("Recognised text",fontWeight=FontWeight.Bold); Text(recognized,color=LifeTrackColors.TextSecondary) } } }
        if(error.isNotBlank()) item { Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
        parsed?.let { p ->
            item { Card(colors=CardDefaults.cardColors(containerColor=LifeTrackColors.Surface),shape=RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("Review before saving",fontSize=17.sp,fontWeight=FontWeight.Bold); var categoryMenu by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded=categoryMenu,onExpandedChange={categoryMenu=!categoryMenu}) { OutlinedTextField(value=editCategory,onValueChange={},readOnly=true,label={Text("Category")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(categoryMenu)},modifier=Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded=categoryMenu,onDismissRequest={categoryMenu=false}) { listOf("Water","Study","Art","Meal","Care","Face routine","Phone usage","Income","Expense").forEach { option -> DropdownMenuItem(text={Text(option)},onClick={editCategory=option;categoryMenu=false}) } } }; OutlinedTextField(value=editTitle,onValueChange={editTitle=it},label={Text("Activity")},singleLine=true,modifier=Modifier.fillMaxWidth()); OutlinedTextField(value=editAmount,onValueChange={editAmount=it.filter { ch -> ch.isDigit() || ch == '.' }.take(12)},label={Text("Amount / minutes / ml")},singleLine=true,modifier=Modifier.fillMaxWidth()); OutlinedTextField(value=editNote,onValueChange={editNote=it},label={Text("Notes")},modifier=Modifier.fillMaxWidth()); Text("Check the category and value before saving. Water uses millilitres, study uses minutes, money uses rupees, and habits or meals use counts.",color=LifeTrackColors.TextSecondary,fontSize=11.sp); Button(onClick={ val value=editAmount.toDoubleOrNull(); val validCategory=editCategory.trim() in setOf("Water","Study","Art","Meal","Care","Face routine","Phone usage","Income","Expense"); val validValue=value!=null && value.isFinite() && value>0 && (editCategory.trim() !in setOf("Water","Study","Art") || value<=100000); if(!validCategory) error="Choose Water, Study, Art, Income or Expense." else if(!validValue) error="Enter a valid positive value." else { onSave(editCategory.trim(),editTitle.trim().ifBlank { editCategory.trim()+" entry" },editAmount,editNote); recognized=""; parsed=null; error="Saved to LifeTrack." } },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=LifeTrackColors.Primary,contentColor=LifeTrackColors.TextPrimary)) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp)); Text("Confirm & save") }; OutlinedButton(onClick={parsed=null;error=""},modifier=Modifier.fillMaxWidth()) { Text("Discard and try again") } } } }
        }
        item { Text("Supported languages depend on the speech-recognition service installed on your phone. This version offers language selection and basic command parsing; it does not yet understand every language or every number format. Speech may be processed by your device's selected recognition provider.",color=LifeTrackColors.TextSecondary,fontSize=11.sp) }
    }
}
