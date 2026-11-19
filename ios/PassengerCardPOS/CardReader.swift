import CoreNFC
import Foundation

final class CardReader: NSObject, NFCTagReaderSessionDelegate {
    private var session: NFCTagReaderSession?
    private var completion: ((Result<String, Error>) -> Void)?

    func scan(completion: @escaping (Result<String, Error>) -> Void) {
        guard session == nil else { return }
        guard NFCTagReaderSession.readingAvailable,
              let reader = NFCTagReaderSession(pollingOption: [.iso14443, .iso15693],
                                               delegate: self, queue: .main) else {
            completion(.failure(POSAPI.APIError("iPhone này chưa sẵn sàng đọc NFC.")))
            return
        }
        self.completion = completion
        session = reader
        reader.alertMessage = "Đưa thẻ ID đến gần cạnh trên của iPhone."
        reader.begin()
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        finish(.failure(error))
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first, tags.count == 1 else {
            session.alertMessage = "Đưa từng thẻ vào vùng đọc."
            session.restartPolling()
            return
        }

        let identifier: Data
        switch tag {
        case let .miFare(card): identifier = card.identifier
        case let .iso7816(card): identifier = card.identifier
        case let .iso15693(card): identifier = card.identifier
        case .feliCa:
            session.invalidate(errorMessage: "Loại thẻ chưa được hỗ trợ.")
            return
        @unknown default:
            session.invalidate(errorMessage: "Loại thẻ chưa được hỗ trợ.")
            return
        }
        guard (4...32).contains(identifier.count) else {
            session.invalidate(errorMessage: "Thẻ không cung cấp mã định danh hợp lệ.")
            return
        }

        session.alertMessage = "Đã đọc thẻ."
        let code = identifier.map { String(format: "%02X", $0) }.joined()
        finish(.success(code))
        session.invalidate()
    }

    private func finish(_ result: Result<String, Error>) {
        let callback = completion
        completion = nil
        session = nil
        callback?(result)
    }
}
