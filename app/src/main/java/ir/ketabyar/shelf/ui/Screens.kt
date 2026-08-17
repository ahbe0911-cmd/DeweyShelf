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
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    Scaffold(containerColor = DeweyTeal) { padding -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
        LibraryHero()
        Surface(color=Color(0xFFF4F8F7),shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=30.dp,topEnd=30.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("انتخاب بخش کتابخانه",fontSize=21.sp,fontWeight=FontWeight.Black,color=DeweyTealDark); Surface(color=DeweyYellow,shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)){Text("آفلاین",Modifier.padding(horizontal=12.dp,vertical=5.dp),fontWeight=FontWeight.Bold,color=DeweyTealDark)} }
                ShelfChoice("کتاب‌های عمومی","رده‌های عمومی دیویی",DeweyTeal,false){onSection(BookSection.GENERAL)}
                ShelfChoice("کتاب‌های ادبیات","ساختار اختصاصی رده 800",Literature,true){onSection(BookSection.LITERATURE)}
                Button(onClick=onList,colors=ButtonDefaults.buttonColors(containerColor=DeweyTealDark),modifier=Modifier.fillMaxWidth().height(58.dp)){Icon(Icons.Outlined.ViewList,null);Spacer(Modifier.width(8.dp));Text("مشاهده چیدمان واقعی قفسه",fontSize=16.sp,fontWeight=FontWeight.Bold)}
                TextButton(onClick=onSettings,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Settings,null);Spacer(Modifier.width(8.dp));Text("تنظیم قواعد و الگوی برچسب")}
            }
        }
    } }
}

@Composable private fun LibraryHero(){ Column(Modifier.fillMaxWidth().padding(top=24.dp,bottom=18.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("چیدمان‌یار دیویی",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Black);Text("کتاب درست، در جای درست",color=DeweyYellow,fontWeight=FontWeight.Bold)};Surface(color=Color.White.copy(.16f),shape=androidx.compose.foundation.shape.CircleShape){Icon(Icons.Outlined.LocalLibrary,null,tint=Color.White,modifier=Modifier.padding(12.dp).size(30.dp))}}
    Canvas(Modifier.padding(top=10.dp).width(250.dp).height(118.dp)){val w=size.width;val h=size.height;drawRect(Color.White,Offset(w*.12f,h*.30f),Size(w*.76f,h*.55f));val roof=Path().apply{moveTo(w*.06f,h*.30f);lineTo(w*.5f,h*.02f);lineTo(w*.94f,h*.30f);close()};drawPath(roof,Color.White);drawRect(DeweyTeal,Offset(w*.43f,h*.48f),Size(w*.14f,h*.37f));listOf(.2f,.32f,.68f,.8f).forEach{x->drawRect(DeweyTeal,Offset(w*x,h*.42f),Size(w*.055f,h*.43f))};drawRect(DeweyYellow,Offset(w*.06f,h*.88f),Size(w*.88f,h*.06f))}
} }

@Composable private fun ShelfChoice(title:String,subtitle:String,color:Color,literature:Boolean,onClick:()->Unit){Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,color.copy(.25f)),modifier=Modifier.fillMaxWidth().height(116.dp)){Row(Modifier.fillMaxSize().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(82.dp).fillMaxHeight().background(color,androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)){repeat(4){i->Box(Modifier.width(if(i==1)14.dp else 11.dp).height((46+i*5).dp).background(if(i==2)DeweyYellow else Color.White,androidx.compose.foundation.shape.RoundedCornerShape(topStart=3.dp,topEnd=3.dp)))}}};Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(title,fontSize=21.sp,fontWeight=FontWeight.Black,color=color);Text(subtitle,color=Color.Gray,fontSize=13.sp);Spacer(Modifier.height(7.dp));Text(if(literature)"8فا /32 …" else "746/755 ر376ع",fontWeight=FontWeight.Bold,color=DeweyTealDark)};Icon(Icons.Outlined.ChevronLeft,null,tint=color)}}}

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

@Composable private fun AddBookScreen(vm: BookViewModel, onBack: () -> Unit) {
    val f by vm.form.collectAsState(); val rules by vm.rules.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (f.section == BookSection.GENERAL) "افزودن کتاب عمومی" else "افزودن کتاب ادبیات") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowForward, "بازگشت") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = if (f.section == BookSection.GENERAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) },
        bottomBar = { Surface(shadowElevation = 10.dp) { Button(onClick = vm::save, modifier = Modifier.padding(16.dp).fillMaxWidth().height(58.dp)) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("بررسی و ذخیره کتاب", fontSize = 18.sp) } } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LabelPreview(LabelFormatter.format(f.code(), rules), f.section) }
            item { VoiceField("عنوان کتاب *", f.title, f.errors["title"]) { vm.change { s -> s.copy(title = it, errors = s.errors - "title") } } }
            item { VoiceField("نام نویسنده", f.authorFirst) { vm.change { s -> s.copy(authorFirst = it) } } }
            item { VoiceField("نام خانوادگی نویسنده", f.authorLast) { vm.change { s -> s.copy(authorLast = it) } } }
            item { VoiceField("موضوع کتاب", f.subject) { vm.change { s -> s.copy(subject = it) } } }
            item { VoiceField("شماره ثبت کتابخانه *", f.registration, f.errors["registrationNumber"]) { vm.change { s -> s.copy(registration = it, errors = s.errors - "registrationNumber") } } }
            if (f.section == BookSection.GENERAL) generalFields(f, vm) else literatureFields(f, vm)
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
    val accent=if(section==BookSection.GENERAL)DeweyTeal else Literature
    Card(colors=CardDefaults.cardColors(containerColor=accent),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("برچسب عطف",color=DeweyYellow,fontWeight=FontWeight.Black,fontSize=18.sp);Text("پیش‌نمایش زنده",color=Color.White.copy(.8f),fontSize=12.sp)};Surface(color=Paper,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),border=BorderStroke(3.dp,Color.White)){Text(label.ifBlank{"—"},Modifier.padding(horizontal=26.dp,vertical=13.dp),fontSize=22.sp,fontWeight=FontWeight.Black,lineHeight=29.sp,color=Color.Black)}}}
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
