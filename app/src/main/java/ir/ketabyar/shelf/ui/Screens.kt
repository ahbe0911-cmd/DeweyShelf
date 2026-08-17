@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.ketabyar.shelf.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.ketabyar.shelf.core.*

private enum class Screen { HOME, GENERAL, LITERATURE, LIST, SETTINGS }

@Composable fun KetabYarRoot(vm: BookViewModel = hiltViewModel()) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (screen) {
            Screen.HOME -> HomeScreen(onSection = { vm.start(it); screen = if (it == BookSection.GENERAL) Screen.GENERAL else Screen.LITERATURE }, onList = { screen = Screen.LIST }, onSettings = { screen = Screen.SETTINGS })
            Screen.GENERAL, Screen.LITERATURE -> AddBookScreen(vm, onBack = { screen = Screen.HOME })
            Screen.LIST -> BookListScreen(vm, onBack = { screen = Screen.HOME })
            Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.HOME })
        }
    }
}

@Composable private fun HomeScreen(onSection: (BookSection) -> Unit, onList: () -> Unit, onSettings: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(24.dp)); Text("کتاب‌یار قفسه", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("ثبت دقیق کتاب و تشخیص محل صحیح در قفسه", fontSize = 16.sp, color = Color.DarkGray)
            SectionCard("کتاب‌های عمومی", "رده‌های 000 تا 999", Icons.Outlined.MenuBook, MaterialTheme.colorScheme.primary) { onSection(BookSection.GENERAL) }
            SectionCard("کتاب‌های ادبیات", "فرم و ترتیب مستقل ادبیات", Icons.Outlined.AutoStories, MaterialTheme.colorScheme.secondary) { onSection(BookSection.LITERATURE) }
            OutlinedButton(onClick = onList, modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(Icons.Outlined.ViewList, null); Spacer(Modifier.width(8.dp)); Text("نمای واقعی قفسه‌ها") }
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Settings, null); Spacer(Modifier.width(8.dp)); Text("تنظیمات قواعد کتابخانه") }
            Spacer(Modifier.weight(1f)); Text("کاملاً آفلاین • بدون دوربین و OCR", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable private fun SettingsScreen(vm: BookViewModel, onBack: () -> Unit) {
    val rules by vm.rules.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("تنظیمات قواعد کتابخانه") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowForward, "بازگشت") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("علامت جداکننده شماره رده", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(rules.separator == "/", { vm.setSeparator("/") }, { Text("ممیز فارسی  / ") })
                FilterChip(rules.separator == ".", { vm.setSeparator(".") }, { Text("نقطه  . ") })
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("تأیید الگوی ادبیات", fontWeight = FontWeight.Bold); Text("فقط پس از بررسی ساختار سطرهای برچسب توسط کتابدار ارشد فعال شود.", color = Color.Gray, fontSize = 13.sp) }
                Switch(rules.literaturePatternConfirmed, vm::confirmLiterature)
            }
            if (!rules.literaturePatternConfirmed) AssistChip(onClick = {}, label = { Text("ثبت ادبیات تا زمان تأیید متوقف است") }, leadingIcon = { Icon(Icons.Outlined.Warning, null) })
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))) { Text("قاعده فعلی عدد مؤلف: مقایسه مانند ادامه اعشاری. حرف و عدد مؤلف هر دو می‌توانند خالی باشند.", Modifier.padding(16.dp)) }
        }
    }
}

@Composable private fun SectionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth().height(132.dp)) {
        Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(44.dp)); Spacer(Modifier.width(18.dp)); Column { Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.White.copy(.8f)) }
        }
    }
}

@Composable private fun AddBookScreen(vm: BookViewModel, onBack: () -> Unit) {
    val f by vm.form.collectAsState(); val rules by vm.rules.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (f.section == BookSection.GENERAL) "افزودن کتاب عمومی" else "افزودن کتاب ادبیات") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowForward, "بازگشت") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = if (f.section == BookSection.GENERAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) },
        bottomBar = { Surface(shadowElevation = 10.dp) { Button(onClick = vm::save, modifier = Modifier.padding(16.dp).fillMaxWidth().height(58.dp)) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("بررسی و ذخیره کتاب", fontSize = 18.sp) } } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LabelPreview(LabelFormatter.format(f.code(), rules), f.section) }
            item { SectionTitle("مشخصات پایه") }
            item { VoiceField("عنوان کتاب *", f.title, f.errors["title"]) { vm.change { s -> s.copy(title = it, errors = s.errors - "title") } } }
            item { VoiceField("نام نویسنده", f.authorFirst) { vm.change { s -> s.copy(authorFirst = it) } } }
            item { VoiceField("نام خانوادگی نویسنده", f.authorLast) { vm.change { s -> s.copy(authorLast = it) } } }
            item { VoiceField("موضوع کتاب", f.subject) { vm.change { s -> s.copy(subject = it) } } }
            item { VoiceField("شماره ثبت کتابخانه *", f.registration, f.errors["registrationNumber"]) { vm.change { s -> s.copy(registration = it, errors = s.errors - "registrationNumber") } } }
            if (f.section == BookSection.GENERAL) generalFields(f, vm) else literatureFields(f, vm)
            item { SectionTitle("اطلاعات تکمیلی") }
            item { VoiceField("جلد", f.volume) { vm.change { s -> s.copy(volume = it) } } }
            item { VoiceField("نسخه", f.edition) { vm.change { s -> s.copy(edition = it) } } }
            item { VoiceField("سال انتشار", f.year, f.errors["year"]) { vm.change { s -> s.copy(year = it, errors = s.errors - "year") } } }
            item { VoiceField("نام قفسه", f.shelf) { vm.change { s -> s.copy(shelf = it) } } }
            item { VoiceField("شماره ردیف", f.row) { vm.change { s -> s.copy(row = it) } } }
            item { VoiceField("توضیحات اختیاری", f.notes, singleLine = false) { vm.change { s -> s.copy(notes = it) } } }
            f.errors["literaturePattern"]?.let { item { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Outlined.Warning, null) }) } }
            f.savedMessage?.let { item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD7F4E8))) { Text(it, Modifier.padding(16.dp), color = Color(0xFF075E59), fontWeight = FontWeight.Bold) } } }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.generalFields(f: BookFormState, vm: BookViewModel) {
    item { SectionTitle("اجزای برچسب عطف") }
    item { VoiceField("شماره اصلی رده (سه رقم) *", f.mainClass, f.errors["mainClass"]) { vm.change { s -> s.copy(mainClass = it, errors = s.errors - "mainClass") } } }
    item { VoiceField("اعشار رده؛ بدون ممیز", f.classDecimal, f.errors["classDecimal"]) { vm.change { s -> s.copy(classDecimal = it, errors = s.errors - "classDecimal") } } }
    item { VoiceField("حرف مؤلف (اختیاری)", f.authorLetter) { vm.change { s -> s.copy(authorLetter = it) } } }
    item { VoiceField("عدد مؤلف (اختیاری)", f.authorNumber, f.errors["authorNumber"]) { vm.change { s -> s.copy(authorNumber = it, errors = s.errors - "authorNumber") } } }
    item { VoiceField("نشانه اثر", f.workMark) { vm.change { s -> s.copy(workMark = it) } } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.literatureFields(f: BookFormState, vm: BookViewModel) {
    item { SectionTitle("ساختار مستقل ادبیات") }
    item { VoiceField("زبان ادبی", f.language) { vm.change { s -> s.copy(language = it) } } }
    item { VoiceField("کد زبان، مانند 8فا *", f.languageCode, f.errors["languageCode"]) { vm.change { s -> s.copy(languageCode = it, errors = s.errors - "languageCode") } } }
    item { VoiceField("دوره یا بخش ادبی *", f.literaturePeriod, f.errors["literaturePeriod"]) { vm.change { s -> s.copy(literaturePeriod = it, errors = s.errors - "literaturePeriod") } } }
    item { VoiceField("نوع اثر", f.workType) { vm.change { s -> s.copy(workType = it) } } }
    item { VoiceField("حرف مؤلف (اختیاری)", f.authorLetter) { vm.change { s -> s.copy(authorLetter = it) } } }
    item { VoiceField("عدد مؤلف (اختیاری)", f.authorNumber, f.errors["authorNumber"]) { vm.change { s -> s.copy(authorNumber = it, errors = s.errors - "authorNumber") } } }
    item { VoiceField("نشانه اثر", f.workMark) { vm.change { s -> s.copy(workMark = it) } } }
    item { VoiceField("حرف عنوان، در صورت استفاده", f.titleLetter) { vm.change { s -> s.copy(titleLetter = it) } } }
}

@Composable private fun LabelPreview(label: String, section: BookSection) {
    Card(colors = CardDefaults.cardColors(containerColor = if (section == BookSection.GENERAL) Color(0xFFE0F2F1) else Color(0xFFF5E7EF)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("پیش‌نمایش زنده برچسب عطف", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Surface(color = Color.White, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, Color.LightGray)) { Text(label.ifBlank { "—" }, Modifier.padding(horizontal = 32.dp, vertical = 16.dp), fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp) } }
    }
}

@Composable private fun VoiceField(label: String, value: String, error: String? = null, singleLine: Boolean = true, onChange: (String) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> pending = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() }
    Column {
        OutlinedTextField(value, onChange, label = { Text(label) }, isError = error != null, singleLine = singleLine, minLines = if (singleLine) 1 else 3, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")) }) { Icon(Icons.Outlined.Mic, "ورود صوتی") } })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) }
        pending?.let { text -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("تأیید متن تشخیص‌داده‌شده") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { onChange(text); pending = null }) { Text("تأیید") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("رد") } }) }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }

@Composable private fun BookListScreen(vm: BookViewModel, onBack: () -> Unit) {
    val books by vm.books.collectAsState(); var query by remember { mutableStateOf("") }; var section by remember { mutableStateOf(BookSection.GENERAL) }
    val shown = books.filter { it.section == section && (query.isBlank() || listOf(it.title, it.authorLastName, it.registrationNumber, it.mainClass).any { v -> PersianNormalizer.normalize(v).contains(PersianNormalizer.normalize(query)) }) }
    Scaffold(topBar = { TopAppBar(title = { Text("نمای واقعی قفسه") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowForward, "بازگشت") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("جست‌وجوی عنوان، نویسنده، ثبت یا رده") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(section == BookSection.GENERAL, { section = BookSection.GENERAL }, { Text("عمومی") }); FilterChip(section == BookSection.LITERATURE, { section = BookSection.LITERATURE }, { Text("ادبیات") }) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(shown) { b -> val index = shown.indexOf(b); Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text("${index + 1}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(b.title, fontWeight = FontWeight.Bold); Text("${b.shelfName} • ردیف ${b.rowNumber ?: "—"}", color = Color.Gray) }; Text(LabelFormatter.format(ir.ketabyar.shelf.core.ShelfCode(b.section,b.mainClass,b.classDecimal,b.languageCode,b.literaturePeriod,b.authorLetter,b.authorNumber,b.workMark,b.titleLetter,b.volume,b.edition,b.year), LibraryRules()), fontWeight = FontWeight.Bold) } } } }
        }
    }
}
