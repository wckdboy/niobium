import Foundation
import AVFoundation
import Speech

@MainActor
final class SpeechTranscriber: ObservableObject {
    @Published var transcript = ""
    @Published var isRecording = false
    @Published var error: String?

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()
    private var startedAt = Date()

    func start() {
        guard !isRecording else { return }
        transcript = ""
        error = nil
        Task {
            let speech = await Self.requestSpeechPermission()
            guard speech else {
                error = "Speech recognition permission is off."
                return
            }
            let mic = await Self.requestMicrophonePermission()
            guard mic else {
                error = "Microphone permission is off."
                return
            }
            begin()
        }
    }

    private func begin() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            request = SFSpeechAudioBufferRecognitionRequest()
            guard let request else { return }
            request.shouldReportPartialResults = true
            request.taskHint = .dictation
            if recognizer?.supportsOnDeviceRecognition == true {
                request.requiresOnDeviceRecognition = true
            }
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                self?.request?.append(buffer)
            }
            engine.prepare()
            try engine.start()
            isRecording = true
            startedAt = Date()
            task = recognizer?.recognitionTask(with: request) { [weak self] result, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let result {
                        self.transcript = result.bestTranscription.formattedString
                    }
                    if error != nil || result?.isFinal == true {
                        self.stop()
                    }
                }
            }
        } catch {
            self.error = error.localizedDescription
            stop()
        }
    }

    func stop() {
        guard isRecording || engine.isRunning else { return }
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        isRecording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private static func requestSpeechPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
    }

    private static func requestMicrophonePermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }
}
