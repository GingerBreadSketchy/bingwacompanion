package ke.co.bingwa.companion

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ke.co.bingwa.companion.automation.FulfillmentService
import ke.co.bingwa.companion.data.CompanionRepository
import ke.co.bingwa.companion.model.AppSettings
import ke.co.bingwa.companion.model.BundleOffer
import ke.co.bingwa.companion.model.FulfillmentJob
import ke.co.bingwa.companion.model.JobStatus
import ke.co.bingwa.companion.ui.theme.BingwaCompanionTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class HomeTab(
    val label: String,
    val icon: ImageVector,
)

@Immutable
data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val offers: List<BundleOffer> = emptyList(),
    val jobs: List<FulfillmentJob> = emptyList(),
)

@Immutable
data class OfferDraft(
    val name: String = "",
    val type: String = "data",
    val priceKes: String = "",
    val ussdCode: String = "",
    val replySequence: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CompanionRepository(application)
    private val _uiState = MutableStateFlow(
        AppUiState(
            settings = repository.getSettings(),
            offers = repository.getOffers(),
            jobs = repository.getJobs(),
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update {
            it.copy(
                settings = repository.getSettings(),
                offers = repository.getOffers(),
                jobs = repository.getJobs(),
            )
        }
    }

    fun saveSettings(next: AppSettings) {
        repository.saveSettings(next)
        _uiState.update { it.copy(settings = next) }
    }

    fun saveOffer(nextOffer: BundleOffer) {
        val offers = _uiState.value.offers
            .map { if (it.id == nextOffer.id) nextOffer else it }
            .sortedWith(compareBy<BundleOffer> { it.type }.thenBy { it.priceKes })
        repository.saveOffers(offers)
        _uiState.update { it.copy(offers = offers) }
    }

    fun toggleOfferActive(offerId: String) {
        val offers = _uiState.value.offers
            .map { offer ->
                if (offer.id == offerId) {
                    offer.copy(active = !offer.active)
                } else {
                    offer
                }
            }
            .sortedWith(compareBy<BundleOffer> { it.type }.thenBy { it.priceKes })
        repository.saveOffers(offers)
        _uiState.update { it.copy(offers = offers) }
    }

    fun removeOffer(offerId: String) {
        val offers = _uiState.value.offers.filterNot { it.id == offerId }
        repository.saveOffers(offers)
        _uiState.update { it.copy(offers = offers) }
    }

    fun addOffer(draft: OfferDraft): Boolean {
        val amount = draft.priceKes.trim().toIntOrNull() ?: return false
        val name = draft.name.trim()
        val ussd = draft.ussdCode.trim()
        if (name.isBlank() || ussd.isBlank()) return false

        val normalizedType = draft.type.trim().lowercase().ifBlank { "data" }
        val replies = draft.replySequence
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val nextOffer = BundleOffer(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            type = normalizedType,
            priceKes = amount,
            ussdCode = ussd,
            active = true,
            replySequence = replies,
        )

        val offers = (_uiState.value.offers + nextOffer)
            .sortedWith(compareBy<BundleOffer> { it.type }.thenBy { it.priceKes })
        repository.saveOffers(offers)
        _uiState.update { it.copy(offers = offers) }
        return true
    }

    fun resetOffers() {
        repository.resetOffers()
        refresh()
    }

    fun exportOffers(context: android.content.Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(repository.exportOffersJson().toByteArray())
            } ?: false
            true
        }.getOrDefault(false)
    }

    fun importOffers(context: android.content.Context, uri: Uri): Boolean {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return false

        val imported = repository.importOffersJson(raw)
        if (imported) {
            refresh()
        }
        return imported
    }

    fun runJob(context: android.content.Context, jobId: String) {
        viewModelScope.launch {
            val intent = Intent(context, FulfillmentService::class.java).apply {
                putExtra(FulfillmentService.EXTRA_JOB_ID, jobId)
            }
            ContextCompat.startForegroundService(context, intent)
            refresh()
        }
    }

    fun runTestUssd(context: android.content.Context, ussdCode: String, simSlot: Int) {
        val normalized = ussdCode.trim()
        if (normalized.isBlank()) {
            return
        }
        viewModelScope.launch {
            val intent = Intent(context, FulfillmentService::class.java).apply {
                putExtra(FulfillmentService.EXTRA_TEST_USSD, normalized)
                putExtra(FulfillmentService.EXTRA_TEST_SIM_SLOT, simSlot)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BingwaCompanionTheme(darkTheme = true) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                var catalogSearch by rememberSaveable { mutableStateOf("") }
                var catalogTypeFilter by rememberSaveable { mutableStateOf("all") }
                var catalogMessage by rememberSaveable { mutableStateOf("Search, filter, export, and import your USSD mappings here. Known seeded USSD codes are restored automatically.") }
                var testUssd by rememberSaveable { mutableStateOf("*180*5*2*pppp*5*1#") }
                var testMessage by rememberSaveable { mutableStateOf("Run a safe device test here before trusting dual-SIM routing on your phone.") }
                var expandedOfferId by rememberSaveable { mutableStateOf<String?>(null) }
                var showTestLab by rememberSaveable { mutableStateOf(false) }
                var showCustomOffer by rememberSaveable { mutableStateOf(false) }
                var currentTab by rememberSaveable { mutableStateOf(0) }
                val tabs = remember {
                    listOf(
                        HomeTab("Dashboard", Icons.Outlined.SpaceDashboard),
                        HomeTab("Setup", Icons.Outlined.PhoneAndroid),
                        HomeTab("Detection", Icons.Outlined.DocumentScanner),
                        HomeTab("Bundles", Icons.Outlined.Layers),
                    )
                }

                val filteredOffers by remember(uiState.offers, catalogSearch, catalogTypeFilter) {
                    derivedStateOf {
                        uiState.offers.filter { offer ->
                            val typeMatches = catalogTypeFilter == "all" || offer.type.equals(catalogTypeFilter, ignoreCase = true)
                            val query = catalogSearch.trim().lowercase()
                            val queryMatches = query.isBlank() ||
                                offer.name.lowercase().contains(query) ||
                                offer.ussdCode.lowercase().contains(query) ||
                                offer.priceKes.toString().contains(query)
                            typeMatches && queryMatches
                        }
                    }
                }

                val smsPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    viewModel.refresh()
                }
                val callPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    viewModel.refresh()
                }
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    if (uri != null) {
                        val ok = viewModel.exportOffers(context, uri)
                        catalogMessage = if (ok) "Offer mappings exported." else "Export failed."
                    }
                }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        val ok = viewModel.importOffers(context, uri)
                        catalogMessage = if (ok) "Offer mappings imported." else "Import failed. Use a valid JSON export from this app."
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.background,
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    NavigationBarItem(
                                        selected = index == currentTab,
                                        onClick = { currentTab = index },
                                        icon = {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        when (currentTab) {
                            0 -> TabContent(modifier = Modifier.padding(padding)) {
                                item {
                                    HeroCard(
                                        jobCount = uiState.jobs.size,
                                        activeOffers = uiState.offers.count { it.active && it.ussdCode.isNotBlank() },
                                        successCount = uiState.jobs.count { it.status == JobStatus.SUCCESS },
                                        delaySeconds = uiState.settings.fulfillmentDelaySeconds,
                                    )
                                }
                                item {
                                    HowItWorksCard()
                                }
                                if (uiState.jobs.isEmpty()) {
                                    item {
                                        EmptyJobsCard()
                                    }
                                } else {
                                    items(uiState.jobs, key = { it.id }) { job ->
                                        JobCard(
                                            job = job,
                                            onRun = { viewModel.runJob(context, job.id) },
                                        )
                                    }
                                }
                                item {
                                    CreditsCard(
                                        onOpenGithub = {
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://github.com/GingerBreadSketchy"),
                                                ),
                                            )
                                        },
                                        onDonate = {
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://gingerpay.vercel.app"),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }

                            1 -> TabContent(modifier = Modifier.padding(padding)) {
                                item {
                                    PermissionCard(
                                        smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS),
                                        callGranted = hasPermission(Manifest.permission.CALL_PHONE),
                                        accessibilityEnabled = isAccessibilityEnabled(),
                                        onGrantSms = {
                                            smsPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.RECEIVE_SMS,
                                                    Manifest.permission.READ_SMS,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ),
                                            )
                                        },
                                        onGrantCall = {
                                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                        },
                                        onOpenAccessibility = {
                                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        },
                                    )
                                }
                                item {
                                    CollapsibleToolsCard(
                                        title = "Quick USSD Test",
                                        subtitle = "Open this only when you want to test dialing on this phone.",
                                        expanded = showTestLab,
                                        onToggle = { showTestLab = !showTestLab },
                                    ) {
                                        TestLabCard(
                                            ussdCode = testUssd,
                                            message = testMessage,
                                            preferredSimSlot = uiState.settings.preferredSimSlot,
                                            onUssdChange = { testUssd = it },
                                            onRun = { simSlot ->
                                                val normalized = testUssd.trim()
                                                if (normalized.isBlank()) {
                                                    testMessage = "Enter a USSD code first."
                                                } else {
                                                    viewModel.runTestUssd(context, normalized, simSlot)
                                                    val label = when (simSlot) {
                                                        1 -> "SIM 1"
                                                        2 -> "SIM 2"
                                                        else -> "Auto"
                                                    }
                                                    testMessage = "Test launch sent using $label routing."
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            2 -> TabContent(modifier = Modifier.padding(padding)) {
                                item {
                                    SettingsCard(
                                        settings = uiState.settings,
                                        onSaveSettings = viewModel::saveSettings,
                                    )
                                }
                            }

                            else -> TabContent(modifier = Modifier.padding(padding)) {
                                item {
                                    CatalogToolsCard(
                                        search = catalogSearch,
                                        selectedType = catalogTypeFilter,
                                        message = catalogMessage,
                                        onSearchChange = { catalogSearch = it },
                                        onTypeChange = { catalogTypeFilter = it },
                                        onExport = {
                                            exportLauncher.launch("bingwa-offers.json")
                                        },
                                        onImport = {
                                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                                        },
                                    )
                                }
                                item {
                                    CollapsibleToolsCard(
                                        title = "Add Your Own Bundle",
                                        subtitle = "Open this only when you want to add a new bundle manually.",
                                        expanded = showCustomOffer,
                                        onToggle = { showCustomOffer = !showCustomOffer },
                                    ) {
                                        CustomOfferCard(
                                            onAdd = viewModel::addOffer,
                                        )
                                    }
                                }
                                item {
                                    SectionHeader(
                                        title = "Bundles",
                                        subtitle = "Search your bundles, adjust USSD codes, or restore the built-in list.",
                                        trailingLabel = "Restore Default List",
                                        onTrailingClick = {
                                            viewModel.resetOffers()
                                            catalogMessage = "Default bundle list restored."
                                        },
                                    )
                                }
                                items(filteredOffers, key = { it.id }) { offer ->
                                    OfferCard(
                                        offer = offer,
                                        expanded = expandedOfferId == offer.id,
                                        onToggleExpand = {
                                            expandedOfferId = if (expandedOfferId == offer.id) null else offer.id
                                        },
                                        onSave = viewModel::saveOffer,
                                        onToggleActive = { viewModel.toggleOfferActive(offer.id) },
                                        onRemove = if (offer.id.startsWith("custom_")) {
                                            { viewModel.removeOffer(offer.id) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                                if (filteredOffers.isEmpty()) {
                                    item {
                                        EmptyCatalogCard()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val serviceId = ComponentName(this, ke.co.bingwa.companion.automation.BingwaAccessibilityService::class.java).flattenToString()
        return enabled.contains(serviceId, ignoreCase = true)
    }
}

@Composable
private fun TabContent(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun HeroCard(
    jobCount: Int,
    activeOffers: Int,
    successCount: Int,
    delaySeconds: Int,
) {
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            Color(0xFF111B1A),
        ),
    )

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Bingwa Companion",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The app waits a little after payment is confirmed, then starts the bundle purchase automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatChip("Jobs", jobCount.toString())
                    StatChip("Active USSD", activeOffers.toString())
                    StatChip("Successes", successCount.toString())
                    StatChip("Delay", "${delaySeconds}s")
                }
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    val steps = listOf(
        "Payment SMS arrives on your phone",
        "App matches it to an active bundle offer",
        "USSD code is dialled after the configured delay",
        "Accessibility service handles any menu replies",
    )

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (index < steps.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionCard(
    smsGranted: Boolean,
    callGranted: Boolean,
    accessibilityEnabled: Boolean,
    onGrantSms: () -> Unit,
    onGrantCall: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(
                title = "Phone Setup",
                subtitle = "Turn on the permissions below so the app can read payment messages and complete purchases for you.",
                icon = Icons.Outlined.SettingsSuggest,
            )
            PermissionRow("Read payment messages", smsGranted, Icons.Outlined.Notifications, onGrantSms)
            PermissionRow("Place USSD calls", callGranted, Icons.Outlined.Phone, onGrantCall)
            PermissionRow("Screen automation", accessibilityEnabled, Icons.Outlined.Security, onOpenAccessibility)
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                }
                Column {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (granted) "Ready" else "Needs setup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
                Text(if (granted) "Manage" else "Allow")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val isDirty = draft != settings

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(
                title = "Payment Detection",
                subtitle = "Main controls stay visible here. Advanced matching is hidden so it is harder to change something by mistake.",
                icon = Icons.Outlined.AutoAwesome,
            )
            OutlinedTextField(
                value = draft.senderFilter,
                onValueChange = { value -> draft = draft.copy(senderFilter = value) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                label = { Text("Message Sender") },
                supportingText = { Text("Add sender names separated by commas, for example MPESA,Paybill,Till.") },
            )
            OutlinedTextField(
                value = draft.successKeywords,
                onValueChange = { value -> draft = draft.copy(successKeywords = value) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                label = { Text("Payment Words") },
            )
            OutlinedTextField(
                value = draft.fulfillmentDelaySeconds.toString(),
                onValueChange = { value ->
                    draft = draft.copy(fulfillmentDelaySeconds = value.filter(Char::isDigit).toIntOrNull() ?: 0)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                label = { Text("Wait Time After Payment (seconds)") },
                supportingText = { Text("Increase this if the bundle purchase starts too quickly after the payment prompt.") },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("SIM to Use", fontWeight = FontWeight.SemiBold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimChoiceChip("Auto", draft.preferredSimSlot == 0) {
                        draft = draft.copy(preferredSimSlot = 0)
                    }
                    SimChoiceChip("SIM 1", draft.preferredSimSlot == 1) {
                        draft = draft.copy(preferredSimSlot = 1)
                    }
                    SimChoiceChip("SIM 2", draft.preferredSimSlot == 2) {
                        draft = draft.copy(preferredSimSlot = 2)
                    }
                }
                Text(
                    "Some phones handle dual-SIM USSD differently, so this setting is best-effort on certain devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start Purchase Automatically", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When turned on, the app will start the bundle purchase after the wait time above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = draft.autoStartFulfillment,
                        onCheckedChange = { checked -> draft = draft.copy(autoStartFulfillment = checked) },
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Advanced matching", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Only open this if payment messages are not being detected correctly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showAdvanced = !showAdvanced }) {
                            Text(if (showAdvanced) "Hide" else "Show")
                        }
                    }
                    if (showAdvanced) {
                        OutlinedTextField(
                            value = draft.phoneRegex,
                            onValueChange = { value -> draft = draft.copy(phoneRegex = value) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            label = { Text("Phone Pattern") },
                        )
                        OutlinedTextField(
                            value = draft.amountRegex,
                            onValueChange = { value -> draft = draft.copy(amountRegex = value) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            label = { Text("Amount Pattern") },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { draft = settings },
                    enabled = isDirty,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Reset")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { onSaveSettings(draft) },
                    enabled = isDirty,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SimChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsibleToolsCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "Hide" else "Open")
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestLabCard(
    ussdCode: String,
    message: String,
    preferredSimSlot: Int,
    onUssdChange: (String) -> Unit,
    onRun: (Int) -> Unit,
) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        OutlinedTextField(
            value = ussdCode,
            onValueChange = onUssdChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            label = { Text("USSD to Test") },
            supportingText = { Text("The app will dial this exactly as written here.") },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRun(0) }, shape = RoundedCornerShape(8.dp)) {
                Text(if (preferredSimSlot == 0) "Run Auto / Preferred" else "Run Auto")
            }
            OutlinedButton(onClick = { onRun(1) }, shape = RoundedCornerShape(8.dp)) {
                Text("Test SIM 1")
            }
            OutlinedButton(onClick = { onRun(2) }, shape = RoundedCornerShape(8.dp)) {
                Text("Test SIM 2")
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomOfferCard(
    onAdd: (OfferDraft) -> Boolean,
) {
    var draft by remember { mutableStateOf(OfferDraft()) }
    var helperText by remember { mutableStateOf("Add your own bundle here if it is missing from the default list.") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            label = { Text("Bundle Name") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = draft.type,
                onValueChange = { draft = draft.copy(type = it) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                label = { Text("Type") },
            )
            OutlinedTextField(
                value = draft.priceKes,
                onValueChange = { draft = draft.copy(priceKes = it.filter(Char::isDigit)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                label = { Text("Price KES") },
            )
        }
        OutlinedTextField(
            value = draft.ussdCode,
            onValueChange = { draft = draft.copy(ussdCode = it) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            label = { Text("USSD Code") },
            supportingText = { Text("If needed, keep `pppp` where the recipient number should go.") },
        )
        OutlinedTextField(
            value = draft.replySequence,
            onValueChange = { draft = draft.copy(replySequence = it) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            label = { Text("Reply Sequence") },
            supportingText = { Text("Optional replies separated by commas if the menu asks follow-up questions.") },
        )
        Text(helperText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = {
                    draft = OfferDraft(type = draft.type)
                    helperText = "Form cleared."
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Clear")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    val added = onAdd(draft)
                    helperText = if (added) {
                        draft = OfferDraft(type = draft.type)
                        "Bundle added."
                    } else {
                        "Please add a name, price, and USSD code."
                    }
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Add Bundle")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogToolsCard(
    search: String,
    selectedType: String,
    message: String,
    onSearchChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                label = { Text("Search Bundles") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "data" to "Data", "minutes" to "Minutes", "sms" to "SMS").forEach { (value, label) ->
                    FilterChip(
                        selected = selectedType == value,
                        onClick = { onTypeChange(value) },
                        shape = RoundedCornerShape(6.dp),
                        label = { Text(label) },
                    )
                }
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImport, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.Upload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import List")
                }
                OutlinedButton(onClick = onExport, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export List")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingLabel != null && onTrailingClick != null) {
            TextButton(onClick = onTrailingClick) {
                Text(trailingLabel)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfferCard(
    offer: BundleOffer,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (BundleOffer) -> Unit,
    onToggleActive: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    var ussdDraft by remember(offer.id, offer.ussdCode) {
        mutableStateOf(offer.ussdCode)
    }
    var replyText by remember(offer.id, offer.replySequence) {
        mutableStateOf(offer.replySequence.joinToString(","))
    }
    val normalizedReplies = remember(replyText) {
        replyText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val isDirty = ussdDraft != offer.ussdCode || normalizedReplies != offer.replySequence

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(offer.name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "KES ${offer.priceKes} · ${offer.type.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = offer.active,
                        onClick = onToggleActive,
                        shape = RoundedCornerShape(6.dp),
                        label = { Text(if (offer.active) "Active" else "Paused") },
                    )
                    if (onRemove != null) {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete offer")
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoPill("ID", offer.id)
                InfoPill("Replies", if (offer.replySequence.isEmpty()) "None" else offer.replySequence.size.toString())
                InfoPill("Custom", if (offer.id.startsWith("custom_")) "Yes" else "Seeded")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (offer.ussdCode.isBlank()) "USSD missing" else offer.ussdCode,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onToggleExpand, shape = RoundedCornerShape(8.dp)) {
                    Text(if (expanded) "Close" else "Edit")
                }
            }
            if (expanded) {
                OutlinedTextField(
                    value = ussdDraft,
                    onValueChange = { value -> ussdDraft = value },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("USSD Code") },
                    supportingText = { Text("Recipient placeholder stays as `pppp`.") },
                )

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { value -> replyText = value },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Reply Sequence") },
                    supportingText = { Text("Comma-separated auto replies, for example `1,1`.") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            ussdDraft = offer.ussdCode
                            replyText = offer.replySequence.joinToString(",")
                        },
                        enabled = isDirty,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Reset")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSave(
                                offer.copy(
                                    ussdCode = ussdDraft.trim(),
                                    replySequence = normalizedReplies,
                                ),
                            )
                        },
                        enabled = isDirty,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Save Offer")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EmptyCatalogCard() {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No bundles match this filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Try a different search, switch the type above, or import another bundle list.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyJobsCard() {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No activity yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "When a payment message matches your settings, it will appear here automatically.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditsCard(
    onOpenGithub: () -> Unit,
    onDonate: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ) {
                    Text(
                        "GH",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Built by GingerBreadSketchy", fontWeight = FontWeight.Bold)
                    Text(
                        "Open GitHub or support the project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenGithub, shape = RoundedCornerShape(8.dp)) {
                    Text("Open GitHub")
                }
                Button(onClick = onDonate, shape = RoundedCornerShape(8.dp)) {
                    Text("Donate")
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    job: FulfillmentJob,
    onRun: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(job.matchedOfferName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "KES ${job.amountKes} · ${job.recipientPhone}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusBadge(job.status)
            }

            Text(
                "USSD: ${job.renderedUssd}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Sender: ${job.sender}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (job.transcript.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    job.transcript.takeLast(3).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onRun, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Run Again")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: JobStatus) {
    val (bg, fg) = when (status) {
        JobStatus.SUCCESS -> Color(0xFF123725) to Color(0xFF8FF0BC)
        JobStatus.FAILED -> Color(0xFF3E1515) to Color(0xFFFFA4A4)
        JobStatus.PROCESSING -> Color(0xFF2B2A14) to Color(0xFFFFDF88)
        JobStatus.REQUIRES_REVIEW -> Color(0xFF20283A) to Color(0xFFA3C9FF)
        JobStatus.QUEUED -> Color(0xFF1D2630) to Color(0xFFB6D6F9)
    }

    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            status.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
