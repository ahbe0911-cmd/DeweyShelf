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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
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
    LaunchedEffect(f.savedMessage) { if (f.savedMessage != null) { kotlinx.coroutines.delay(1350); onBack() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (f.section == BookSection.GENERAL) "افزودن کتاب عمومی" else "افزودن کتاب ادبیات") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowForward, "بازگشت") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = if (f.section == BookSection.GENERAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) },
        bottomBar = { Surface(shadowElevation = 10.dp, modifier=Modifier.navigationBarsPadding()) { Button(onClick = vm::save, modifier = Modifier.padding(horizontal=16.dp,vertical=10.dp).fillMaxWidth().height(54.dp)) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("ذخیره کتاب", fontSize = 17.sp, fontWeight=FontWeight.Bold) } } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LabelPreview(LabelFormatter.format(f.code(), rules), f.section) }
            item { if (f.section == BookSection.GENERAL) GeneralSpineFields(f, vm) else LiteratureSpineFields(f, vm) }
            f.errors["literaturePattern"]?.let { item { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Outlined.Warning, null) }) } }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
    if(f.savedMessage!=null){Dialog(onDismissRequest={}){androidx.compose.animation.AnimatedVisibility(visible=true,enter=androidx.compose.animation.fadeIn()+androidx.compose.animation.scaleIn(initialScale=.72f)){Surface(color=Color(0xFF11875D),shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp),shadowElevation=18.dp){Column(Modifier.padding(horizontal=42.dp,vertical=30.dp),horizontalAlignment=Alignment.CenterHorizontally){Surface(color=Color.White.copy(.18f),shape=androidx.compose.foundation.shape.CircleShape){Icon(Icons.Outlined.CheckCircle,"ذخیره شد",tint=Color.White,modifier=Modifier.padding(12.dp).size(46.dp))};Spacer(Modifier.height(12.dp));Text("ذخیره شد",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black);Text("کتاب با موفقیت ثبت شد",color=Color.White.copy(.85f),fontSize=13.sp)}}}}}
}

@Composable private fun GeneralSpineFields(f:BookFormState,vm:BookViewModel){val d=remember{FocusRequester()};val l=remember{FocusRequester()};val n=remember{FocusRequester()};val m=remember{FocusRequester()};val r=remember{FocusRequester()};Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text("اجزای برچسب عطف",fontSize=19.sp,fontWeight=FontWeight.Black,color=DeweyTeal);Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){VoiceField("رده اصلی *",f.mainClass,f.errors["mainClass"],modifier=Modifier.weight(1f),imeAction=ImeAction.Next,onNext={d.requestFocus()},autoNextLength=3){vm.change{s->s.copy(mainClass=it.take(3),errors=s.errors-"mainClass")}};VoiceField("اعشار رده",f.classDecimal,f.errors["classDecimal"],modifier=Modifier.weight(1f).focusRequester(d),imeAction=ImeAction.Next,onNext={l.requestFocus()}){vm.change{s->s.copy(classDecimal=it,errors=s.errors-"classDecimal")}}};Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){VoiceField("حرف مؤلف",f.authorLetter,modifier=Modifier.weight(1f).focusRequester(l),imeAction=ImeAction.Next,onNext={n.requestFocus()},autoNextLength=1){vm.change{s->s.copy(authorLetter=it.take(1))}};VoiceField("عدد مؤلف",f.authorNumber,f.errors["authorNumber"],modifier=Modifier.weight(1f).focusRequester(n),imeAction=ImeAction.Next,onNext={m.requestFocus()}){vm.change{s->s.copy(authorNumber=it,errors=s.errors-"authorNumber")}}};Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){VoiceField("نشانه اثر",f.workMark,modifier=Modifier.weight(1f).focusRequester(m),imeAction=ImeAction.Next,onNext={r.requestFocus()},autoNextLength=1){vm.change{s->s.copy(workMark=it.take(1))}};VoiceField("شماره ثبت",f.registration,f.errors["registrationNumber"],modifier=Modifier.weight(1f).focusRequester(r),imeAction=ImeAction.Done){vm.change{s->s.copy(registration=it,errors=s.errors-"registrationNumber")}}}}}

@Composable private fun LiteratureSpineFields(f:BookFormState,vm:BookViewModel){val p=remember{FocusRequester()};val l=remember{FocusRequester()};val n=remember{FocusRequester()};val m=remember{FocusRequester()};val t=remember{FocusRequester()};val r=remember{FocusRequester()};Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text("اجزای برچسب ادبیات",fontSize=19.sp,fontWeight=FontWeight.Black,color=Literature);Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){VoiceField("کد زبان *",f.languageCode,f.errors["languageCode"],modifier=Modifier.weight(1f),imeAction=ImeAction.Next,onNext={p.requestFocus()}){vm.change{s->s.copy(languageCode=it,errors=s.errors-"languageCode")}};VoiceField("دوره ادبی *",f.literaturePeriod,f.errors["literaturePeriod"],modifier=Modifier.weight(1f).focusRequester(p),imeAction=ImeAction.Next,onNext={l.requestFocus()}){vm.change{s->s.copy(literaturePeriod=it,errors=s.errors-"literaturePeriod")}}};Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){VoiceField("حرف مؤلف",f.authorLetter,modifier=Modifier.weight(1f).focusRequester(l),imeAction=ImeAction.Next,onNext={n.requestFocus()},autoNextLength=1){vm.change{s->s.copy(authorLetter=it.take(1))}};VoiceField("عدد مؤلف",f.authorNumber,f.errors["authorNumber"],modifier=Modifier.weight(1f).focusRequester(n),imeAction=ImeAction.Next,onNext={m.requestFocus()}){vm.change{s->s.copy(authorNumber=it,errors=s.errors-"authorNumber")}}};Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){VoiceField("نشانه اثر",f.workMark,modifier=Modifier.weight(1f).focusRequester(m),imeAction=ImeAction.Next,onNext={t.requestFocus()},autoNextLength=1){vm.change{s->s.copy(workMark=it.take(1))}};VoiceField("حرف عنوان",f.titleLetter,modifier=Modifier.weight(1f).focusRequester(t),imeAction=ImeAction.Next,onNext={r.requestFocus()},autoNextLength=1){vm.change{s->s.copy(titleLetter=it.take(1))}}};VoiceField("شماره ثبت",f.registration,f.errors["registrationNumber"],modifier=Modifier.fillMaxWidth().focusRequester(r),imeAction=ImeAction.Done){vm.change{s->s.copy(registration=it,errors=s.errors-"registrationNumber")}}}}

@Composable private fun LabelPreview(label: String, section: BookSection) {
    val accent=if(section==BookSection.GENERAL)DeweyTeal else Literature
    Card(colors=CardDefaults.cardColors(containerColor=accent),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("برچسب عطف واقعی",color=DeweyYellow,fontWeight=FontWeight.Black,fontSize=19.sp);Text("اطلاعات دقیقاً سطر‌به‌سطر روی عطف نمایش داده می‌شود",color=Color.White.copy(.82f),fontSize=12.sp,modifier=Modifier.padding(end=6.dp));Spacer(Modifier.height(10.dp));Text("نمونه قفسه کتابخانه",color=Color.White,fontWeight=FontWeight.Bold)};Spine(label.ifBlank{"—"},accent,false,Modifier.width(104.dp).height(184.dp))}}
}

@Composable private fun Spine(label:String,color:Color,highlight:Boolean,modifier:Modifier=Modifier){Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=9.dp,topEnd=9.dp,bottomStart=3.dp,bottomEnd=3.dp),border=BorderStroke(if(highlight)3.dp else 1.dp,if(highlight)DeweyYellow else Color.White.copy(.55f)),shadowElevation=10.dp,modifier=modifier){Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(color.copy(.72f),color,color.copy(.82f))))){Box(Modifier.fillMaxHeight().width(5.dp).align(Alignment.CenterStart).background(Color.White.copy(.18f)));Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.CenterHorizontally){Text("کد عطف",color=Color.White.copy(.8f),fontSize=10.sp,modifier=Modifier.padding(top=12.dp));Surface(color=Color(0xFFFFFCF3),border=BorderStroke(1.dp,Color(0xFFD8D1C2)),shadowElevation=3.dp,modifier=Modifier.fillMaxWidth().padding(horizontal=7.dp)){Text(label,textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=20.sp,fontWeight=FontWeight.Black,lineHeight=27.sp,color=Color(0xFF202020),modifier=Modifier.padding(vertical=10.dp))};Box(Modifier.fillMaxWidth().height(30.dp).background(DeweyYellow),contentAlignment=Alignment.Center){Icon(Icons.Outlined.AutoStories,null,tint=DeweyTealDark,modifier=Modifier.size(18.dp))}}}}}

@Composable private fun PlacementShelf(previous:String?,current:String,next:String?,label:String){Card(colors=CardDefaults.cardColors(containerColor=DeweyTealDark),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("محل دقیق قرارگیری",color=DeweyYellow,fontSize=20.sp,fontWeight=FontWeight.Black);Text(if(previous==null)"ابتدای این بخش از قفسه" else if(next==null)"انتهای این بخش از قفسه" else "بین دو کتاب زیر",color=Color.White);Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth().height(190.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.Bottom){ShelfBook(previous?:"ابتدای قفسه",Color(0xFF5E7FA5));Spacer(Modifier.width(5.dp));Column(horizontalAlignment=Alignment.CenterHorizontally){Text("کتاب جدید",color=DeweyYellow,fontSize=11.sp,fontWeight=FontWeight.Bold);Spine(label,DeweyYellow,true,Modifier.width(96.dp).height(172.dp))};Spacer(Modifier.width(5.dp));ShelfBook(next?:"انتهای قفسه",Literature)};HorizontalDivider(color=DeweyYellow,thickness=5.dp);Spacer(Modifier.height(9.dp));Text("بعد از: ${previous?:"—"}",color=Color.White,fontWeight=FontWeight.Bold);Text("قبل از: ${next?:"—"}",color=Color.White,fontWeight=FontWeight.Bold)}}}

@Composable private fun ShelfBook(title:String,color:Color){Surface(color=color,shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=6.dp,topEnd=6.dp),modifier=Modifier.width(82.dp).height(154.dp)){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=4,modifier=Modifier.padding(8.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center);Box(Modifier.fillMaxWidth().height(52.dp).background(Paper),contentAlignment=Alignment.Center){Text("برچسب",color=Color.DarkGray,fontSize=10.sp)}}}}

@Composable private fun VoiceField(label: String, value: String, error: String? = null, singleLine: Boolean = true, modifier:Modifier=Modifier, imeAction:ImeAction=ImeAction.Next, onNext:()->Unit={}, autoNextLength:Int?=null, onChange: (String) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> pending = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() }
    Column {
        OutlinedTextField(value, {v->onChange(v);if(autoNextLength!=null&&v.length>=autoNextLength)onNext()}, label = { Text(label,fontSize=11.sp,maxLines=1) }, textStyle=MaterialTheme.typography.bodyMedium, isError = error != null, singleLine = singleLine, minLines = 1, keyboardOptions=KeyboardOptions(imeAction=imeAction),keyboardActions=KeyboardActions(onNext={onNext()},onDone={onNext()}), modifier = modifier.then(if(singleLine)Modifier.height(56.dp) else Modifier), trailingIcon = { IconButton(onClick = { launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")) },modifier=Modifier.size(34.dp)) { Icon(Icons.Outlined.Mic, "ورود صوتی",modifier=Modifier.size(18.dp)) } })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) }
        pending?.let { text -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("تأیید متن تشخیص‌داده‌شده") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { onChange(text); pending = null }) { Text("تأیید") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("رد") } }) }
    }
}

@Composable private fun SuccessMessage(){Surface(color=Color(0xFF158A5B),shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Icon(Icons.Outlined.CheckCircle,"ذخیره شد",tint=Color.White);Spacer(Modifier.width(8.dp));Text("کتاب با موفقیت ذخیره شد",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Black)}}}

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
