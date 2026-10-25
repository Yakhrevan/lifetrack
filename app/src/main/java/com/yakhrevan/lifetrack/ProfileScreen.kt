package com.yakhrevan.lifetrack

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Locale

// ==========================================
// 1. DESIGN TOKENS (THEME-AWARE)
// ==========================================
private val DarkBackground = Color(0xFF090D1A)
private val CardBackground = Color(0xFF121929)
private val CardInner      = Color(0xFF1B2438)
private val CardBorderDark = Color(0x1FFFFFFF)
private val PurpleBrand    = Color(0xFFA855F7)
private val CyanBrand      = Color(0xFF06B6D4)
private val EmeraldBrand   = Color(0xFF10B981)
private val AmberBrand     = Color(0xFFF59E0B)
private val CoralBrand     = Color(0xFFF43F5E)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMutedDark  = Color(0xFF94A3B8)

@Composable
private fun profileColors(): ProfileThemeColors {
    val isDark = LifeTrackColors.darkMode.value
    return ProfileThemeColors(
        isDark = isDark,
        screenBg = if (isDark) DarkBackground else LifeTrackColors.Background,
        cardBg = if (isDark) CardBackground else LifeTrackColors.Surface,
        cardInner = if (isDark) CardInner else LifeTrackColors.SurfaceRaised,
        cardBorder = if (isDark) CardBorderDark else Color(0x1A000000),
        textPrimary = if (isDark) TextWhite else LifeTrackColors.TextPrimary,
        textSecondary = if (isDark) TextMutedDark else LifeTrackColors.TextSecondary
    )
}

private data class ProfileThemeColors(
    val isDark: Boolean,
    val screenBg: Color,
    val cardBg: Color,
    val cardInner: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

// Avatar definition
enum class AvatarCategory(val label: String) {
    ALL("All"),
    DEFAULT("LifeTrack"),
    NEUTRAL("Neutral"),
    FEMALE("Female"),
    MALE("Male")
}

data class BuiltInAvatar(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val icon: ImageVector,
    val hairColor: Color = Color(0xFF2D1B4E),
    val category: AvatarCategory = AvatarCategory.NEUTRAL
)

val BUILT_IN_AVATARS = listOf(
    // 1. Official Default LifeTrack Avatar
    BuiltInAvatar("lifetrack_default", "LifeTrack", Color(0xFF6366F1), Color(0xFFA855F7), Icons.Default.AutoAwesome, category = AvatarCategory.DEFAULT),

    // 2. Gender-Neutral Options
    BuiltInAvatar("avatar_7", "Kai", Color(0xFF14B8A6), Color(0xFF10B981), Icons.Default.Person, category = AvatarCategory.NEUTRAL),
    BuiltInAvatar("avatar_10", "Nova", Color(0xFF06B6D4), Color(0xFF38BDF8), Icons.Default.Star, category = AvatarCategory.NEUTRAL),
    BuiltInAvatar("avatar_11", "Zen", Color(0xFF10B981), Color(0xFF84CC16), Icons.Default.Spa, category = AvatarCategory.NEUTRAL),
    BuiltInAvatar("avatar_12", "Cosmo", Color(0xFFA855F7), Color(0xFFEC4899), Icons.Default.TrackChanges, category = AvatarCategory.NEUTRAL),
    BuiltInAvatar("avatar_17", "Echo", Color(0xFF6366F1), Color(0xFF38BDF8), Icons.Default.Pets, category = AvatarCategory.NEUTRAL),
    BuiltInAvatar("avatar_18", "Bloom", Color(0xFFF43F5E), Color(0xFFF59E0B), Icons.Default.LocalFlorist, category = AvatarCategory.NEUTRAL),

    // 3. Female / Girl Options
    BuiltInAvatar("avatar_1", "Priya", Color(0xFFEC4899), Color(0xFF8B5CF6), Icons.Default.Face, category = AvatarCategory.FEMALE),
    BuiltInAvatar("avatar_4", "Elena", Color(0xFFF59E0B), Color(0xFFF97316), Icons.Default.Face2, category = AvatarCategory.FEMALE),
    BuiltInAvatar("avatar_6", "Aria", Color(0xFFF43F5E), Color(0xFFEC4899), Icons.Default.Face6, category = AvatarCategory.FEMALE),
    BuiltInAvatar("avatar_8", "Maya", Color(0xFF8B5CF6), Color(0xFF6366F1), Icons.Default.Face, category = AvatarCategory.FEMALE),
    BuiltInAvatar("avatar_13", "Chloe", Color(0xFF06B6D4), Color(0xFF3B82F6), Icons.Default.Face2, category = AvatarCategory.FEMALE),
    BuiltInAvatar("avatar_14", "Zara", Color(0xFF10B981), Color(0xFF059669), Icons.Default.Face6, category = AvatarCategory.FEMALE),

    // 4. Male / Boy Options
    BuiltInAvatar("avatar_2", "Alex", Color(0xFF38BDF8), Color(0xFF3B82F6), Icons.Default.Face3, category = AvatarCategory.MALE),
    BuiltInAvatar("avatar_3", "Marcus", Color(0xFF10B981), Color(0xFF06B6D4), Icons.Default.Face4, category = AvatarCategory.MALE),
    BuiltInAvatar("avatar_5", "Dev", Color(0xFF6366F1), Color(0xFFA855F7), Icons.Default.Face5, category = AvatarCategory.MALE),
    BuiltInAvatar("avatar_9", "Rohan", Color(0xFFFF9F1C), Color(0xFFEAB308), Icons.Default.Face3, category = AvatarCategory.MALE),
    BuiltInAvatar("avatar_15", "Leo", Color(0xFF8B5CF6), Color(0xFF4C1D95), Icons.Default.Face5, category = AvatarCategory.MALE),
    BuiltInAvatar("avatar_16", "Ethan", Color(0xFFEC4899), Color(0xFFBE185D), Icons.Default.Face4, category = AvatarCategory.MALE)
)

// ==========================================
// 2. MAIN PROFILE SCREEN (WITH ROUTING)
// ==========================================

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToPreferences: () -> Unit = {},
    onNavigateToDataBackup: () -> Unit = {},
    onNavigateToPersonalisation: () -> Unit = onNavigateToPreferences,
    onNavigateToInsights: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentSubScreen by remember { mutableStateOf("Main") } // "Main", "ChoosePicture", "PersonalInfo"
    var profile by remember { mutableStateOf(UserProfileRepository.getProfile(context)) }
    var showUsageSummary by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showPhotoActionsSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = currentSubScreen != "Main") {
        currentSubScreen = "Main"
    }

    // Direct Photo Picker launcher from main avatar or dialog
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedUri = UserProfileRepository.saveCustomAvatar(context, uri)
            if (savedUri != null) {
                profile = UserProfileRepository.getProfile(context)
                Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fallback getContent launcher if photo picker unsupported
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedUri = UserProfileRepository.saveCustomAvatar(context, uri)
            if (savedUri != null) {
                profile = UserProfileRepository.getProfile(context)
                Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchImagePicker() {
        runCatching {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            getContentLauncher.launch("image/*")
        }
    }

    when (currentSubScreen) {
        "ChoosePicture" -> {
            ChooseProfilePictureScreen(
                currentAvatarId = profile.avatarId,
                currentAvatarUri = profile.avatarUri,
                onSelectAvatar = { id, uri ->
                    UserProfileRepository.updateAvatar(context, id, uri)
                    profile = UserProfileRepository.getProfile(context)
                    currentSubScreen = "Main"
                },
                onPickCustomPhoto = { launchImagePicker() },
                onRemoveCustomPhoto = {
                    UserProfileRepository.removeCustomAvatar(context)
                    profile = UserProfileRepository.getProfile(context)
                },
                onBack = { currentSubScreen = "Main" },
                modifier = modifier
            )
        }
        "PersonalInfo" -> {
            PersonalInformationScreen(
                initialProfile = profile,
                onSave = { updated ->
                    UserProfileRepository.saveProfile(context, updated)
                    profile = updated
                    currentSubScreen = "Main"
                    Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                },
                onBack = { currentSubScreen = "Main" },
                modifier = modifier
            )
        }
        else -> {
            ProfileMainContent(
                profile = profile,
                onAvatarClick = { showPhotoActionsSheet = true },
                onEditPicture = { currentSubScreen = "ChoosePicture" },
                onEditPersonalInfo = { currentSubScreen = "PersonalInfo" },
                onOpenGoals = onNavigateToGoals,
                onOpenPreferences = onNavigateToPersonalisation,
                onOpenUsageSummary = onNavigateToInsights,
                onOpenDataBackup = onNavigateToDataBackup,
                onOpenSettings = onNavigateToSettings,
                onOpenGenderDialog = { showGenderDialog = true },
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }
    }

    if (showPhotoActionsSheet) {
        val colors = profileColors()
        AlertDialog(
            onDismissRequest = { showPhotoActionsSheet = false },
            containerColor = colors.cardBg,
            title = {
                Text(
                    "Profile Photo",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose how you would like to represent yourself:",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))

                    // 1. Choose from gallery
                    Button(
                        onClick = {
                            showPhotoActionsSheet = false
                            launchImagePicker()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Upload from Gallery / Photos", fontWeight = FontWeight.Bold)
                    }

                    // 2. Select built-in avatar
                    OutlinedButton(
                        onClick = {
                            showPhotoActionsSheet = false
                            currentSubScreen = "ChoosePicture"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose an Avatar")
                    }

                    // 3. Remove custom photo if one is set
                    if (profile.avatarUri != null) {
                        OutlinedButton(
                            onClick = {
                                showPhotoActionsSheet = false
                                UserProfileRepository.removeCustomAvatar(context)
                                profile = UserProfileRepository.getProfile(context)
                                Toast.makeText(context, "Custom photo removed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CoralBrand.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralBrand)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remove Custom Photo")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoActionsSheet = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showUsageSummary) {
        AppUsageSummaryDialog(onDismiss = { showUsageSummary = false })
    }

    if (showGenderDialog) {
        GenderSelectionDialog(
            currentGender = profile.gender,
            onSelect = { selected ->
                UserProfileRepository.updateGender(context, selected)
                profile = UserProfileRepository.getProfile(context)
                showGenderDialog = false
            },
            onDismiss = { showGenderDialog = false }
        )
    }
}

// ==========================================
// 3. SCREEN 1: PROFILE MAIN VIEW
// ==========================================

@Composable
private fun ProfileMainContent(
    profile: UserProfile,
    onAvatarClick: () -> Unit,
    onEditPicture: () -> Unit,
    onEditPersonalInfo: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenUsageSummary: () -> Unit,
    onOpenDataBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGenderDialog: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = profileColors()

    Surface(color = colors.screenBg, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // Header: Back, Title & Settings Cog
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                        Column {
                            Text("Profile", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Your personal space", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(42.dp)
                            .background(colors.cardBg, CircleShape)
                            .border(1.dp, colors.cardBorder, CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PurpleBrand, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Central Profile Avatar & Identity Card
            item {
                Surface(
                    color = colors.cardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Avatar with Camera Badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PurpleBrand, CyanBrand)
                                        )
                                    )
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardBg)
                                    .clickable(onClick = onAvatarClick),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarVisual(profile = profile, sizeDp = 88)
                            }

                            // Edit / Camera Badge
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(PurpleBrand)
                                    .border(2.dp, colors.cardBg, CircleShape)
                                    .clickable(onClick = onAvatarClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Change Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Display Name
                        Text(
                            text = profile.displayName.ifBlank { "Add your name" },
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // Bio / Status (Clickable to edit)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onEditPersonalInfo)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = profile.bio.ifBlank { "Stay consistent, grow everyday" },
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.Edit, contentDescription = "Edit Bio", tint = PurpleBrand, modifier = Modifier.size(12.dp))
                        }

                        // Gender Pill
                        Surface(
                            color = colors.cardInner,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.clickable(onClick = onOpenGenderDialog)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    when (profile.gender) {
                                        "Female" -> Icons.Default.Female
                                        "Male" -> Icons.Default.Male
                                        else -> Icons.Default.Person
                                    },
                                    contentDescription = null,
                                    tint = PurpleBrand,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    profile.gender.ifBlank { "Select Gender" },
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Quick Action Buttons Row: Edit Profile | Change Photo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onEditPersonalInfo,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = onAvatarClick,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Change Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Menu Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileMenuRow(
                        icon = Icons.Default.ManageAccounts,
                        iconTint = PurpleBrand,
                        title = "Personal Information",
                        subtitle = "Name, gender, bio, location, email",
                        onClick = onEditPersonalInfo,
                        colors = colors
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.EmojiEvents,
                        iconTint = CoralBrand,
                        title = "Personal Goals",
                        subtitle = "Create and manage your own targets",
                        onClick = onOpenGoals,
                        colors = colors
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Tune,
                        iconTint = CyanBrand,
                        title = "Preferences",
                        subtitle = "Theme, units, date & time, language",
                        onClick = onOpenPreferences,
                        colors = colors
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Insights,
                        iconTint = EmeraldBrand,
                        title = "App Activity Summary",
                        subtitle = "Your local activity statistics",
                        onClick = onOpenUsageSummary,
                        colors = colors
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Shield,
                        iconTint = AmberBrand,
                        title = "Data & Privacy",
                        subtitle = "Backup, export and privacy controls",
                        onClick = onOpenDataBackup,
                        colors = colors
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        iconTint = PurpleBrand,
                        title = "Settings",
                        subtitle = "Notifications, theme & app behaviour",
                        onClick = onOpenSettings,
                        colors = colors
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    colors: ProfileThemeColors
) {
    Surface(
        color = colors.cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

// ==========================================
// 4. SCREEN 2: CHOOSE PROFILE PICTURE
// ==========================================

@Composable
fun ChooseProfilePictureScreen(
    currentAvatarId: String,
    currentAvatarUri: String?,
    onSelectAvatar: (String, String?) -> Unit,
    onPickCustomPhoto: () -> Unit,
    onRemoveCustomPhoto: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = profileColors()
    var selectedId by remember { mutableStateOf(currentAvatarId) }
    var selectedUri by remember { mutableStateOf(currentAvatarUri) }

    // Keep selectedUri in sync if updated externally (e.g. from photo picker)
    LaunchedEffect(currentAvatarUri) {
        selectedUri = currentAvatarUri
        if (currentAvatarUri != null) {
            selectedId = "custom_gallery"
        }
    }

    Surface(color = colors.screenBg, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Text("Choose Profile Picture", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))

            // CRITICAL FIX: Custom Photo Section prominently placed at TOP
            Surface(
                color = colors.cardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Preview of currently selected custom photo or avatar
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(colors.cardInner)
                            .border(2.dp, PurpleBrand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarVisual(
                            profile = UserProfile(avatarId = selectedId, avatarUri = selectedUri),
                            sizeDp = 56
                        )
                    }

                    val currentSelectedAvatar = BUILT_IN_AVATARS.find { it.id == selectedId }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (selectedUri != null) "Custom Photo Selected" else "Avatar preview",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (selectedUri != null) "Stored safely on your device"
                            else "Choose an avatar or upload your own photo · Tap Done to confirm",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onPickCustomPhoto,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (selectedUri != null) "Change Photo" else "Upload Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (selectedUri != null) {
                                OutlinedButton(
                                    onClick = {
                                        onRemoveCustomPhoto()
                                        selectedUri = null
                                        selectedId = "lifetrack_default"
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CoralBrand.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralBrand),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Subtitle for avatars
            Text("Choose an inclusive avatar", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(8.dp))

            // Show avatars together without gender/category filters or sample names.
            Spacer(Modifier.height(14.dp))

            // Avatar Grid (4 columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(BUILT_IN_AVATARS, key = { it.id }) { avatar ->
                    val isChosen = selectedId == avatar.id && selectedUri == null
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedId = avatar.id
                                selectedUri = null
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(avatar.primaryColor, avatar.secondaryColor))
                                )
                                .border(
                                    width = if (isChosen) 3.dp else 1.dp,
                                    color = if (isChosen) Color.White else colors.cardBorder,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(avatar.icon, contentDescription = "Select avatar", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Done Button
            Button(
                onClick = { onSelectAvatar(selectedId, selectedUri) },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 5. SCREEN 3: PERSONAL INFORMATION
// ==========================================

@Composable
fun PersonalInformationScreen(
    initialProfile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = profileColors()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialProfile.displayName) }
    var gender by remember { mutableStateOf(initialProfile.gender) }
    var dob by remember { mutableStateOf(initialProfile.dateOfBirth) }
    var bio by remember { mutableStateOf(initialProfile.bio) }
    var location by remember { mutableStateOf(initialProfile.location) }
    var email by remember { mutableStateOf(initialProfile.email) }
    var isDetectingLocation by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                isDetectingLocation = true
                val result = LocationHelper.detectLocation(context)
                isDetectingLocation = false
                when (result) {
                    is LocationResult.Success -> {
                        location = result.fullLocation
                        Toast.makeText(context, "Location updated: ${result.cityName}", Toast.LENGTH_SHORT).show()
                    }
                    is LocationResult.ServicesDisabled -> {
                        Toast.makeText(context, "Please turn on GPS/Location services", Toast.LENGTH_LONG).show()
                    }
                    is LocationResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(color = colors.screenBg, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                    Text("Personal Information", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val updated = initialProfile.copy(
                            displayName = name.trim(),
                            gender = gender,
                            dateOfBirth = dob.trim(),
                            bio = bio.trim(),
                            location = location.trim(),
                            email = email.trim()
                        )
                        onSave(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Display Name
                item {
                    Text("Display Name *", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Your name", color = colors.textSecondary) },
                        singleLine = true,
                        colors = outlinedTextFieldColors(colors)
                    )
                }

                // Gender Selection
                item {
                    Text("Gender", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Female", "Male", "Prefer not to say").forEach { option ->
                            val isSelected = gender.equals(option, ignoreCase = true)
                            Surface(
                                color = if (isSelected) PurpleBrand else colors.cardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) PurpleBrand else colors.cardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        gender = if (isSelected) "" else option
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        option,
                                        color = if (isSelected) Color.White else colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Date of Birth
                item {
                    Text("Date of Birth (Optional)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("14 Jan 2000", color = colors.textSecondary) },
                        trailingIcon = { Icon(Icons.Default.CalendarToday, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = outlinedTextFieldColors(colors)
                    )
                }

                // Bio
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bio (Optional)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${bio.length}/200", color = colors.textSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 200) bio = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        placeholder = { Text("Stay consistent, grow everyday", color = colors.textSecondary) },
                        colors = outlinedTextFieldColors(colors)
                    )
                }

                // Location with GPS auto-detection
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Location (Optional)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(
                            onClick = {
                                if (LocationHelper.hasLocationPermission(context)) {
                                    scope.launch {
                                        isDetectingLocation = true
                                        val result = LocationHelper.detectLocation(context)
                                        isDetectingLocation = false
                                        when (result) {
                                            is LocationResult.Success -> {
                                                location = result.fullLocation
                                                Toast.makeText(context, "Location detected: ${result.cityName}", Toast.LENGTH_SHORT).show()
                                            }
                                            is LocationResult.ServicesDisabled -> {
                                                Toast.makeText(context, "Please turn on GPS/Location services", Toast.LENGTH_LONG).show()
                                            }
                                            is LocationResult.Error -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {}
                                        }
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isDetectingLocation) "Detecting..." else "Use GPS", color = CyanBrand, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your location or city", color = colors.textSecondary) },
                        trailingIcon = { Icon(Icons.Default.LocationOn, null, tint = CyanBrand, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = outlinedTextFieldColors(colors)
                    )
                }

                // Email
                item {
                    Text("Email (Optional)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your email", color = colors.textSecondary) },
                        trailingIcon = { Icon(Icons.Default.Email, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        colors = outlinedTextFieldColors(colors)
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. HELPER COMPONENTS & DIALOGS
// ==========================================

@Composable
fun AvatarVisual(profile: UserProfile, sizeDp: Int = 40) {
    val context = LocalContext.current
    if (!profile.avatarUri.isNullOrBlank()) {
        val bitmap = remember(profile.avatarUri) {
            runCatching {
                val uri = Uri.parse(profile.avatarUri)
                val stream: InputStream? = if (uri.scheme == "file") {
                    java.io.FileInputStream(java.io.File(uri.path ?: ""))
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                stream?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(sizeDp.dp)
                    .clip(CircleShape)
            )
            return
        }
    }

    val avatar = BUILT_IN_AVATARS.find { it.id == profile.avatarId } ?: BUILT_IN_AVATARS.first()
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(avatar.primaryColor, avatar.secondaryColor))),
        contentAlignment = Alignment.Center
    ) {
        Icon(avatar.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((sizeDp * 0.55f).dp))
    }
}

@Composable
private fun GenderSelectionDialog(
    currentGender: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = profileColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        title = { Text("Select Gender", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Female", "Male", "Prefer not to say", "Clear").forEach { option ->
                    val isChosen = currentGender.equals(option, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isChosen) PurpleBrand.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable {
                                if (option == "Clear") onSelect("") else onSelect(option)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = isChosen,
                            onClick = { if (option == "Clear") onSelect("") else onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = PurpleBrand)
                        )
                        Text(option, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PurpleBrand)
            }
        }
    )
}

@Composable
private fun AppUsageSummaryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = profileColors()
    val repository = remember { LifeTrackRepository(context) }
    val logs = remember { repository.loadEntries() }
    val tasks = remember { TaskRepository.getTasks(context) }
    val notes = remember { NoteRepository.getNotes(context) }

    val daysActive = remember(logs) { logs.map { it.date }.distinct().size.coerceAtLeast(if (logs.isNotEmpty()) 1 else 0) }
    val studyMin = remember(logs) { logs.filter { it.category.equals("Study", true) }.sumOf { it.amount.toDoubleOrNull()?.toInt() ?: 0 } }
    val waterMl = remember(logs) { logs.filter { it.category.equals("Water", true) }.sumOf { it.amount.toDoubleOrNull()?.toInt() ?: 0 } }
    val completedTasks = remember(tasks) { tasks.count { it.isCompleted } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PieChart, contentDescription = null, tint = EmeraldBrand)
                Text("App Activity Summary", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Actual statistics recorded locally on this device.", color = colors.textSecondary, fontSize = 11.sp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard("Active Days", "$daysActive days", CyanBrand, colors, Modifier.weight(1f))
                    SummaryMetricCard("Total Logs", "${logs.size} entries", PurpleBrand, colors, Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard("Study Time", "${studyMin / 60}h ${studyMin % 60}m", AmberBrand, colors, Modifier.weight(1f))
                    SummaryMetricCard("Water Logged", String.format(Locale.US, "%.1f L", waterMl / 1000f), CyanBrand, colors, Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard("Tasks Done", "$completedTasks / ${tasks.size}", EmeraldBrand, colors, Modifier.weight(1f))
                    SummaryMetricCard("Notes Saved", "${notes.size} notes", PurpleBrand, colors, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = PurpleBrand, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    tint: Color,
    colors: ProfileThemeColors,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colors.cardInner,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = colors.textSecondary, fontSize = 10.sp)
            Text(value, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun outlinedTextFieldColors(colors: ProfileThemeColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurpleBrand,
    unfocusedBorderColor = colors.cardBorder,
    focusedLabelColor = PurpleBrand,
    unfocusedLabelColor = colors.textSecondary,
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textPrimary,
    cursorColor = PurpleBrand,
    focusedContainerColor = colors.cardInner,
    unfocusedContainerColor = colors.cardInner
)
