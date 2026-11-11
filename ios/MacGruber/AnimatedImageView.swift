import ImageIO
import SwiftUI
import UIKit

/// Shows a picture filling its frame. GIFs, animated PNGs and animated WebPs play on a loop.
struct AnimatedImageView: UIViewRepresentable {
    let url: URL

    final class Coordinator {
        var url: URL?
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .scaleAspectFill
        view.clipsToBounds = true
        for axis in [NSLayoutConstraint.Axis.horizontal, .vertical] {
            view.setContentHuggingPriority(.defaultLow, for: axis)
            view.setContentCompressionResistancePriority(.defaultLow, for: axis)
        }
        return view
    }

    func updateUIView(_ view: UIImageView, context: Context) {
        let coordinator = context.coordinator
        guard coordinator.url != url else { return }
        coordinator.url = url
        view.image = nil
        let target = url
        DispatchQueue.global(qos: .userInitiated).async {
            let image = ImageLoader.load(target, maxPixelSize: 360)
            DispatchQueue.main.async {
                if coordinator.url == target {
                    view.image = image
                }
            }
        }
    }

    /// Take exactly the space offered, never the picture's own size.
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIImageView, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 0, height: proposal.height ?? 0)
    }
}

enum ImageLoader {
    private static let maxFrames = 300

    /// Decodes a picture scaled down to about tile size; animated files become an animated UIImage.
    static func load(_ url: URL, maxPixelSize: Int) -> UIImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        let count = CGImageSourceGetCount(source)
        if count <= 1 {
            guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                return nil
            }
            return UIImage(cgImage: image)
        }

        var frames: [UIImage] = []
        var duration = 0.0
        for index in 0..<min(count, maxFrames) {
            guard let image = CGImageSourceCreateThumbnailAtIndex(source, index, options as CFDictionary) else {
                continue
            }
            frames.append(UIImage(cgImage: image))
            duration += frameDelay(source, index)
        }
        if frames.isEmpty { return nil }
        if frames.count == 1 { return frames[0] }
        return UIImage.animatedImage(with: frames, duration: duration)
    }

    private static func frameDelay(_ source: CGImageSource, _ index: Int) -> Double {
        let fallback = 0.1
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any] else {
            return fallback
        }
        let formats: [(CFString, CFString, CFString)] = [
            (kCGImagePropertyGIFDictionary, kCGImagePropertyGIFUnclampedDelayTime, kCGImagePropertyGIFDelayTime),
            (kCGImagePropertyPNGDictionary, kCGImagePropertyAPNGUnclampedDelayTime, kCGImagePropertyAPNGDelayTime),
            (kCGImagePropertyWebPDictionary, kCGImagePropertyWebPUnclampedDelayTime, kCGImagePropertyWebPDelayTime),
        ]
        for (dictionaryKey, unclampedKey, clampedKey) in formats {
            guard let dictionary = properties[dictionaryKey] as? [CFString: Any] else { continue }
            let unclamped = (dictionary[unclampedKey] as? NSNumber)?.doubleValue ?? 0
            let clamped = (dictionary[clampedKey] as? NSNumber)?.doubleValue ?? 0
            let delay = unclamped > 0 ? unclamped : clamped
            // Browsers treat very short delays as 0.1s; do the same so GIFs don't race.
            return delay < 0.02 ? fallback : delay
        }
        return fallback
    }
}
