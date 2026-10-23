import SwiftUI
import AVFoundation
import UniformTypeIdentifiers

final class Model: ObservableObject {
    @Published var files: [URL] = []
    @Published var idx = -1
    @Published var playing = false
    @Published var pos = 0.0
    @Published var dur = 0.0
    @Published var eq = false
    @Published var gains = [Double](repeating: 0, count: 10)
    @Published var pre = 0.0
    @Published var info = "Add music to begin"
    private var timer: Timer?

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in self?.tick() }
    }

    func add(_ urls: [URL]) {
        for u in urls {
            let ok = u.startAccessingSecurityScopedResource()
            defer { if ok { u.stopAccessingSecurityScopedResource() } }
            let dst = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "_" + u.lastPathComponent)
            if (try? FileManager.default.copyItem(at: u, to: dst)) != nil { files.append(dst) }
        }
        if idx < 0 && !files.isEmpty { load(0) }
    }

    func load(_ i: Int) {
        guard i >= 0 && i < files.count else { return }
        if ll_open(files[i].path) != 0 { info = "Could not open this file"; return }
        idx = i
        ll_eq(eq ? 1 : 0)
        ll_play(); playing = true
        let r = ll_source_rate(), b = ll_source_bits(), o = ll_output_rate()
        let k = { (x: UInt32) in String(format: "%g", Double(x) / 1000) }
        let src = b > 0 ? "FLAC \(k(r)) kHz / \(b)-bit \((r > 44100 || b > 16) ? "Hi-Res" : "CD quality")" : "Source \(k(r)) kHz"
        info = "\(src)  |  Output stream \(k(o)) kHz (iOS may still resample)"
    }

    func toggle() {
        guard idx >= 0 else { return }
        if ll_is_playing() == 1 { ll_pause(); playing = false } else { ll_play(); playing = true }
    }
    func step(_ d: Int) { if !files.isEmpty { load((idx + d + files.count) % files.count) } }

    func tick() {
        guard idx >= 0 else { return }
        pos = ll_position(); dur = ll_duration()
        if ll_finished() == 1 { if idx + 1 < files.count { load(idx + 1) } else { playing = false } }
    }
}

struct ContentView: View {
    @StateObject var m = Model()
    @State var importing = false
    let labels = ["31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(spacing: 2) {
                    Text("L O S S L E S S").font(.title3).foregroundColor(.gray)
                    Text("TanimSarker").font(.caption2).foregroundColor(.gray.opacity(0.7))
                }.frame(maxWidth: .infinity)
                Text(m.idx >= 0 ? m.files[m.idx].lastPathComponent : "Add music to begin").font(.headline)
                Text(m.info).font(.caption).foregroundColor(.blue)
                Slider(value: Binding(get: { m.dur > 0 ? m.pos / m.dur : 0 },
                                      set: { ll_seek($0 * m.dur) }))
                HStack { Text(fmt(m.pos)); Spacer(); Text(fmt(m.dur)) }.font(.caption).foregroundColor(.gray)
                HStack(spacing: 24) {
                    Button("Add files") { importing = true }
                    Button("Prev") { m.step(-1) }
                    Button(m.playing ? "Pause" : "Play") { m.toggle() }
                    Button("Next") { m.step(1) }
                }.frame(maxWidth: .infinity)
                Toggle("Equalizer on (off = bypass)", isOn: Binding(get: { m.eq }, set: { m.eq = $0; ll_eq($0 ? 1 : 0) }))
                ForEach(0..<10, id: \.self) { i in
                    HStack {
                        Text(labels[i] + " Hz").font(.caption).frame(width: 64, alignment: .leading)
                        Slider(value: Binding(get: { m.gains[i] }, set: { m.gains[i] = $0; ll_band(Int32(i), Float($0)) }), in: -12...12)
                    }
                }
                HStack {
                    Text("Preamp").font(.caption).frame(width: 64, alignment: .leading)
                    Slider(value: Binding(get: { m.pre }, set: { m.pre = $0; ll_preamp(Float($0)) }), in: -12...6)
                }
                ForEach(Array(m.files.enumerated()), id: \.offset) { i, u in
                    Button(u.lastPathComponent) { m.load(i) }.foregroundColor(.primary).lineLimit(1)
                }
            }.padding()
        }
        .preferredColorScheme(.dark)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.audio], allowsMultipleSelection: true) { r in
            if case .success(let urls) = r { m.add(urls) }
        }
    }

    func fmt(_ s: Double) -> String { String(format: "%d:%02d", Int(s) / 60, Int(s) % 60) }
}

@main
struct LosslessApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}
