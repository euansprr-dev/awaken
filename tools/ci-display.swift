// The main display's modes on a GitHub macOS runner (.github/workflows/tours.yml): lists them, and given <w>x<h> (points)
// switches to that size, preferring the 2x (Retina) mode, so tour shots match the Mac's 2880x1800 when the runner allows.
// Usage: swift tools/ci-display.swift [<w>x<h>]
import CoreGraphics
import Foundation

let display = CGMainDisplayID()
let options = [kCGDisplayShowDuplicateLowResolutionModes: kCFBooleanTrue] as CFDictionary
let modes = (CGDisplayCopyAllDisplayModes(display, options) as? [CGDisplayMode]) ?? []
func describe(_ m: CGDisplayMode) -> String { "\(m.width)x\(m.height) pt = \(m.pixelWidth)x\(m.pixelHeight) px" }
if let current = CGDisplayCopyDisplayMode(display) { print("display \(display) now \(describe(current))") }
var seen = Set<String>()
for m in modes where seen.insert(describe(m)).inserted { print("  mode \(describe(m))\(m.isUsableForDesktopGUI() ? "" : " (not for the desktop)")") }
let want = CommandLine.arguments.dropFirst().first?.split(separator: "x").compactMap { Int($0) } ?? []
if want.count == 2 {
    let fits = modes.filter { $0.width == want[0] && $0.height == want[1] }
    guard let mode = fits.max(by: { $0.pixelWidth < $1.pixelWidth }) else { print("no \(want[0])x\(want[1]) mode"); exit(1) }
    var config: CGDisplayConfigRef?
    CGBeginDisplayConfiguration(&config)
    CGConfigureDisplayWithDisplayMode(config, display, mode, nil)
    let err = CGCompleteDisplayConfiguration(config, .forSession)
    print("set \(describe(mode)): \(err == .success ? "ok" : "error \(err.rawValue)")")
    exit(err == .success ? 0 : 1)
}
