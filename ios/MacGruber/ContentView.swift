import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @StateObject private var store = SoundStore()
    @Environment(\.scenePhase) private var scenePhase

    /// The button whose customise dialog is open.
    @State private var editing: Sound?
    @State private var showImportDialog = false

    /// What the Files picker is choosing for. One picker is shared because SwiftUI
    /// only honours one fileImporter per view.
    private enum FilesTarget {
        case clip(Sound), picture(Sound), clipsFolder, gifsFolder
    }
    @State private var filesTarget: FilesTarget?
    @State private var showFiles = false

    @State private var photosTarget: Sound?
    @State private var showPhotos = false
    @State private var photoItem: PhotosPickerItem?

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(store.sounds) { sound in
                        TileView(
                            sound: sound,
                            picture: store.pictureURL(for: sound),
                            isCustomClip: store.hasCustomClip(sound),
                            isPlaying: store.playingKey == sound.key
                        )
                        .onTapGesture {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            store.play(sound)
                        }
                        .onLongPressGesture(minimumDuration: 0.45) {
                            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                            editing = sound
                        }
                    }
                }
                .padding(14)
            }
            bottomBar
        }
        .background(Theme.black.ignoresSafeArea())
        .overlay(alignment: .bottom) { toast }
        .overlay {
            if let sound = editing {
                customiseDialog(sound)
            } else if showImportDialog {
                importDialog
            }
        }
        .animation(.easeOut(duration: 0.15), value: editing)
        .animation(.easeOut(duration: 0.15), value: showImportDialog)
        .animation(.easeOut(duration: 0.2), value: store.toast)
        .fileImporter(isPresented: $showFiles, allowedContentTypes: filesTypes,
                      allowsMultipleSelection: filesAllowsMultiple, onCompletion: handleFiles)
        .photosPicker(isPresented: $showPhotos, selection: $photoItem, matching: .images,
                      preferredItemEncoding: .current)
        .onChange(of: photoItem) { item in
            handlePhoto(item)
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active: store.reload()
            case .background: store.stop()
            default: break
            }
        }
    }

    // MARK: - Bars

    private var header: some View {
        HStack(spacing: 12) {
            Image("Mascot")
                .resizable()
                .scaledToFit()
                .clipShape(Circle())
                .padding(3)
                .background(Circle().fill(Theme.fire))
                .overlay(Circle().strokeBorder(Theme.gold, lineWidth: 3))
                .frame(width: 72, height: 72)
                .accessibilityLabel("Cartoon mullet commando")
            VStack(spacing: 2) {
                Text("MACGRUBER")
                    .font(.system(size: 30, weight: .black))
                    .tracking(2.4)
                    .foregroundColor(Theme.gold)
                    .shadow(color: Theme.fire, radius: 0, x: 3, y: 3)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text("SOUNDBOARD")
                    .font(.system(size: 13, weight: .bold))
                    .tracking(6.5)
                    .foregroundColor(.white)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Theme.olive.ignoresSafeArea(edges: .top))
    }

    private var bottomBar: some View {
        HStack(spacing: 10) {
            Text("Hold a button to add your own clip or picture")
                .font(.system(size: 13))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            Button("IMPORT") { showImportDialog = true }
                .buttonStyle(SolidButtonStyle(kind: .green))
            Button("STOP") { store.stop() }
                .buttonStyle(SolidButtonStyle(kind: .red))
        }
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .padding(.vertical, 10)
        .background(Theme.olive.ignoresSafeArea(edges: .bottom))
    }

    @ViewBuilder
    private var toast: some View {
        if let message = store.toast {
            Text(message)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Capsule().fill(Color.black.opacity(0.85)))
                .overlay(Capsule().strokeBorder(Theme.gold, lineWidth: 1))
                .padding(.bottom, 84)
                .padding(.horizontal, 24)
                .transition(.opacity)
                .allowsHitTesting(false)
        }
    }

    // MARK: - Dialogs

    private func customiseDialog(_ sound: Sound) -> some View {
        ModalDialog(title: "Customise “\(sound.label)”",
                    message: "Swap in your own sound or picture, like a clip you recorded or your favourite MacGruber photo.",
                    onDismiss: { editing = nil }) {
            Button("CHOOSE SOUND CLIP") {
                editing = nil
                openFiles(.clip(sound))
            }
            .buttonStyle(SolidButtonStyle(kind: .green, fullWidth: true))
            Button("CHOOSE PICTURE FROM PHOTOS") {
                editing = nil
                photosTarget = sound
                showPhotos = true
            }
            .buttonStyle(SolidButtonStyle(kind: .green, fullWidth: true))
            Button("CHOOSE PICTURE FROM FILES") {
                editing = nil
                openFiles(.picture(sound))
            }
            .buttonStyle(SolidButtonStyle(kind: .green, fullWidth: true))
        } footer: {
            if store.hasCustomisation(sound) {
                Button("RESET BUTTON") {
                    store.reset(sound)
                    editing = nil
                }
                .buttonStyle(SolidButtonStyle(kind: .green))
            }
            Button("CANCEL") { editing = nil }
                .buttonStyle(SolidButtonStyle(kind: .red))
        }
    }

    private var importDialog: some View {
        ModalDialog(title: "Import your own files",
                    message: "Each sound clip becomes a button, named from its file name. Pictures and GIFs go on the buttons, matched by name. You can also drop files into the MacGruber folder under On My iPhone in the Files app.",
                    onDismiss: { showImportDialog = false }) {
            Button("IMPORT SOUND CLIPS") {
                showImportDialog = false
                openFiles(.clipsFolder)
            }
            .buttonStyle(SolidButtonStyle(kind: .green, fullWidth: true))
            Button("IMPORT PICTURES & GIFS") {
                showImportDialog = false
                openFiles(.gifsFolder)
            }
            .buttonStyle(SolidButtonStyle(kind: .green, fullWidth: true))
        } footer: {
            Button("CANCEL") { showImportDialog = false }
                .buttonStyle(SolidButtonStyle(kind: .red))
        }
    }

    // MARK: - Pickers

    private func openFiles(_ target: FilesTarget) {
        filesTarget = target
        showFiles = true
    }

    private var filesTypes: [UTType] {
        guard let target = filesTarget else { return [.item] }
        switch target {
        case .clip, .clipsFolder: return [.audio]
        case .picture, .gifsFolder: return [.image]
        }
    }

    private var filesAllowsMultiple: Bool {
        guard let target = filesTarget else { return false }
        switch target {
        case .clipsFolder, .gifsFolder: return true
        case .clip, .picture: return false
        }
    }

    private func handleFiles(_ result: Result<[URL], Error>) {
        let pending = filesTarget
        filesTarget = nil
        guard let target = pending, case .success(let urls) = result, let first = urls.first else { return }
        switch target {
        case .clip(let sound): store.setCustomClip(from: first, for: sound)
        case .picture(let sound): store.setCustomPicture(from: first, for: sound)
        case .clipsFolder: store.importFiles(urls, into: .clips)
        case .gifsFolder: store.importFiles(urls, into: .gifs)
        }
    }

    private func handlePhoto(_ item: PhotosPickerItem?) {
        guard let item, let sound = photosTarget else { return }
        photosTarget = nil
        photoItem = nil
        let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
        Task {
            if let data = try? await item.loadTransferable(type: Data.self) {
                store.setCustomPicture(data: data, fileExtension: ext, for: sound)
            } else {
                store.show("Couldn't load that picture.")
            }
        }
    }
}

// MARK: - Tile

struct TileView: View {
    let sound: Sound
    let picture: URL?
    let isCustomClip: Bool
    let isPlaying: Bool

    private let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.orange, Theme.fire], startPoint: .top, endPoint: .bottom)
            if let picture {
                AnimatedImageView(url: picture)
                LinearGradient(colors: [Color.black.opacity(0.13), Color.black.opacity(0.2), Color.black.opacity(0.8)],
                               startPoint: .top, endPoint: .bottom)
            }
            VStack(spacing: 2) {
                Text(sound.emoji)
                    .font(.system(size: 32))
                Text(sound.label)
                    .font(.system(size: 15, weight: .black))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .shadow(color: .black.opacity(0.8), radius: 2, x: 2, y: 2)
                if isCustomClip {
                    Text("★ your clip")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.gold)
                        .shadow(color: .black.opacity(0.8), radius: 1.5, x: 1, y: 1)
                }
            }
            .padding(6)
            if isPlaying {
                shape.fill(Theme.gold.opacity(0.4))
                shape.strokeBorder(Color.white, lineWidth: 4)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 116)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Theme.gold, lineWidth: 2))
        .contentShape(shape)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(sound.label)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Plays the sound. Hold to customise.")
    }
}

// MARK: - Modal

/// A dialog with an olive title bar and an olive button bar, like the Android app's.
struct ModalDialog<Actions: View, Footer: View>: View {
    let title: String
    let message: String
    let onDismiss: () -> Void
    @ViewBuilder let actions: () -> Actions
    @ViewBuilder let footer: () -> Footer

    var body: some View {
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            VStack(spacing: 0) {
                Text(title)
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(Theme.olive)
                Text(message)
                    .font(.system(size: 15))
                    .foregroundColor(Theme.grey)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 12)
                VStack(spacing: 10) {
                    actions()
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
                HStack(spacing: 8) {
                    Spacer(minLength: 0)
                    footer()
                }
                .padding(10)
                .background(Theme.olive)
            }
            .background(Theme.black)
            .overlay(Rectangle().strokeBorder(Theme.gold, lineWidth: 2))
            .frame(maxWidth: 480)
            .padding(16)
            .transition(.opacity)
        }
    }
}
