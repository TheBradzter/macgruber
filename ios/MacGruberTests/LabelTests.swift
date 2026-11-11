import UIKit
import XCTest
@testable import MacGruber

final class LabelTests: XCTestCase {
    func testStripsNumberPrefixAndSeparators() {
        XCTAssertEqual(Labels.fromFileName("03_kfbr-392"), "KFBR 392")
    }

    func testCollapsesSpacesAndSeparators() {
        XCTAssertEqual(Labels.fromFileName("  macgruber   is__here.. "), "MACGRUBER IS HERE")
    }

    func testKeepsNameWhenItIsOnlyDigits() {
        XCTAssertEqual(Labels.fromFileName("01"), "01")
    }

    func testEveryBuiltInSoundIsBundled() {
        XCTAssertEqual(BuiltIn.sounds.count, 20)
        for sound in BuiltIn.sounds {
            XCTAssertNotNil(Bundle.main.url(forResource: sound.resource, withExtension: "m4a"),
                            "missing \(sound.key).m4a")
        }
    }

    func testMascotAndColorsAreInTheAssetCatalog() {
        XCTAssertNotNil(UIImage(named: "Mascot"))
    }
}
